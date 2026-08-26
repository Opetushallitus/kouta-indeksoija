#!/usr/bin/env python3
"""Generoi yhdyssanojen pilkkojan (hyphenation_decompounder) sanalistan.

Miksi tämä on olemassa
----------------------
`finnish_decompound` ja `swedish_decompound` ajetaan analyysiketjussa
lemmatisoinnin (raudikko / hunspell) JÄLKEEN, joten pilkkoja näkee vain
perusmuotoja. Sanalistan pitää siksi sisältää perusmuotoja.

Aiemmin listana käytettiin yleiskielistä sanalistaa (fi 94 000, sv 153 000
sanaa). Tavutuspohjainen pilkkoja poimii tavurajojen välistä minkä tahansa
listalta löytyvän merkkijonon, joten iso yleislista tuottaa valtavasti
roskaa: "avoin" -> "voi", "logistiikka" -> "tii", "perusopinnot" -> "ruso",
"teknologia" -> "tekno" + "nolo". Samalla listalta puuttui perusmuotoja
(esim. "opinto"), joten oikeat pilkkomiset jäivät tekemättä.

Ratkaisu: lista generoidaan indeksoidusta datasta. Mukaan otetaan vain ne
perusmuodot, jotka tosiasiassa esiintyvät tarjonnan teksteissä itsenäisinä
sanoina. Tällainen sana on lähtökohtaisesti kelvollinen yhdyssanan osa;
"tii" ja "ruso" eivät esiinny itsenäisinä eivätkä siten päädy listalle.

Käyttö
------
Tuotanto- tai testiympäristöä vasten (suositeltu):

    ./tools/generate_decompound_wordlist.py \\
        --elastic-url http://localhost:9200 \\
        --lang fi \\
        --out elastic/decompound/fi/words-lemmat.txt

Valmiista tekstilistasta (yksi teksti per rivi), esim. CI:ssä tai ilman
ES-yhteyttä. --analyze-url osoittaa mihin tahansa ES:ään, jossa on
kouta-indeksien analysaattorit:

    ./tools/generate_decompound_wordlist.py \\
        --texts-file /tmp/nimet-fi.txt \\
        --analyze-url http://localhost:9200/koulutus-kouta-search \\
        --lang fi --out elastic/decompound/fi/words-lemmat.txt

Listan muutos vaatii ES-imagen uudelleenrakennuksen (elastic/build.sh) ja
hakuindeksien uudelleenindeksoinnin.
"""

import argparse
import collections
import json
import os
import sys
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))

# Analysaattori, jolla korpuksen sanat muunnetaan perusmuotoon. Tämä on sama
# analysaattori, jota haku käyttää kyselypuolella, eli listalle päätyvät
# täsmälleen ne muodot, joita vasten hakusanoja verrataan.
LEMMATIZER = {"fi": "finnish_lemmatizer", "sv": "swedish_hunspell"}

# Indeksit, joista korpus luetaan.
SEARCH_INDICES = ["koulutus-kouta-search", "oppilaitos-kouta-search"]

# Kentät search_terms-rakenteessa, joista korpus kerätään. Nämä ovat samat
# kentät, joita vapaa sanahaku kohdistuu (ks. konfo-backend search/tools.clj).
CORPUS_FIELDS = [
    "koulutusnimi",
    "toteutusNimi",
    "asiasanat",
    "tutkintonimikkeet",
    "ammattinimikkeet",
    "koulutus_organisaationimi",
    "toteutus_organisaationimi",
    "nimi",
]

BATCH = 250

# Lyhyt sana on vaarallinen yhdyssanan osana: se osuu tavurajojen väliin
# vahingossa. Vaadimme siksi lyhyiltä sanoilta, että ne esiintyvät korpuksessa
# itsenäisenä sanana riittävän usein. Pitkät sanat kelpaavat aina — 6 merkin
# osuma tavurajojen välissä ei käytännössä ole sattumaa.
#
# Kynnykset on valittu mittaamalla: esim. suomenkielisessä korpuksessa
# "ala" 116, "työ" 55, "osa" 311 (kelvollisia yhdyssanan osia), kun taas
# "tie" 4, "maa" 4, "sala" 1, "tulos" 1 (roskaa, joka tuottaisi vääriä osumia).
MIN_LEN = 3
MIN_FREQ_BY_LEN = {3: 20, 4: 10, 5: 3}
MIN_FREQ_DEFAULT = 1


def passes_frequency(word, freq):
    return freq.get(word, 0) >= MIN_FREQ_BY_LEN.get(len(word), MIN_FREQ_DEFAULT)


def _post(url, payload):
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=180) as resp:
        return json.loads(resp.read().decode("utf-8"))


def read_wordfile(path):
    if not os.path.exists(path):
        return set()
    with open(path, encoding="utf-8") as f:
        return {
            line.strip().lower()
            for line in f
            if line.strip() and not line.startswith("#")
        }


def collect_texts_from_elastic(elastic_url, lang):
    """Lukee kaikki lang-kieliset tekstit hakuindeksien search_terms-kentistä."""
    texts = set()
    source = ["search_terms.%s.%s" % (f, lang) for f in CORPUS_FIELDS]
    for index in SEARCH_INDICES:
        after = None
        while True:
            body = {
                "size": 500,
                "_source": source,
                "sort": [{"_doc": "asc"}],
                "query": {"match_all": {}},
            }
            if after:
                body["search_after"] = after
            try:
                res = _post("%s/%s/_search" % (elastic_url.rstrip("/"), index), body)
            except urllib.error.HTTPError as e:
                sys.exit("Haku indeksistä %s epäonnistui: %s" % (index, e.read().decode()))
            hits = res["hits"]["hits"]
            if not hits:
                break
            for hit in hits:
                _harvest(hit.get("_source", {}), texts)
            after = hits[-1]["sort"]
        print("  %-28s %d tekstiä" % (index, len(texts)), file=sys.stderr)
    return texts


def _harvest(node, out):
    """Kerää kaikki merkkijonot rekursiivisesti (search_terms on lista objekteja)."""
    if isinstance(node, str):
        out.add(node)
    elif isinstance(node, list):
        for item in node:
            _harvest(item, out)
    elif isinstance(node, dict):
        for value in node.values():
            _harvest(value, out)


def lemmatize(analyze_url, analyzer, texts):
    """Ajaa tekstit ES:n _analyze-rajapinnan läpi ja palauttaa perusmuotojen
    esiintymämäärät."""
    freq = collections.Counter()
    texts = sorted(texts)
    for i in range(0, len(texts), BATCH):
        payload = {"analyzer": analyzer, "text": texts[i : i + BATCH]}
        try:
            res = _post("%s/_analyze" % analyze_url.rstrip("/"), payload)
        except urllib.error.HTTPError as e:
            sys.exit("_analyze epäonnistui: %s" % e.read().decode())
        for token in res["tokens"]:
            freq[token["token"].lower()] += 1
    return freq


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lang", choices=["fi", "sv"], required=True)
    ap.add_argument("--out", required=True, help="Kirjoitettava sanalista")
    ap.add_argument("--elastic-url", help="ES, josta korpus luetaan (esim. http://localhost:9200)")
    ap.add_argument("--texts-file", help="Vaihtoehto --elastic-url:lle: tekstit tiedostosta, rivi per teksti")
    ap.add_argument(
        "--analyze-url",
        help="Indeksi, jonka analysaattoria käytetään (oletus: <elastic-url>/koulutus-kouta-search)",
    )
    ap.add_argument("--seed", help="Aina mukaan otettavat sanat (oletus: tools/decompound-seed-<lang>.txt)")
    ap.add_argument("--deny", help="Aina pois jätettävät sanat (oletus: tools/decompound-deny-<lang>.txt)")
    args = ap.parse_args()

    if not args.elastic_url and not args.texts_file:
        ap.error("anna joko --elastic-url tai --texts-file")

    analyze_url = args.analyze_url or "%s/%s" % (args.elastic_url.rstrip("/"), SEARCH_INDICES[0])
    seed_path = args.seed or os.path.join(HERE, "decompound-seed-%s.txt" % args.lang)
    deny_path = args.deny or os.path.join(HERE, "decompound-deny-%s.txt" % args.lang)

    if args.texts_file:
        with open(args.texts_file, encoding="utf-8") as f:
            texts = {line.strip() for line in f if line.strip()}
        print("Korpus tiedostosta: %d tekstiä" % len(texts), file=sys.stderr)
    else:
        print("Luetaan korpus indekseistä...", file=sys.stderr)
        texts = collect_texts_from_elastic(args.elastic_url, args.lang)

    if not texts:
        sys.exit("Korpus on tyhjä — tarkista indeksit ja kieli.")

    freq = lemmatize(analyze_url, LEMMATIZER[args.lang], texts)
    print("Perusmuotoja korpuksessa: %d" % len(freq), file=sys.stderr)

    seed = read_wordfile(seed_path)
    deny = read_wordfile(deny_path)

    from_corpus = {
        w for w in freq if len(w) >= MIN_LEN and w.isalpha() and passes_frequency(w, freq)
    }
    words = (from_corpus | {w for w in seed if len(w) >= MIN_LEN and w.isalpha()}) - deny

    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(sorted(words)) + "\n")

    print(
        "Kirjoitettu %s: %d sanaa (korpuksesta %d/%d, siemenlista %d, poistolista %d)"
        % (args.out, len(words), len(from_corpus), len(freq), len(seed), len(deny)),
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
