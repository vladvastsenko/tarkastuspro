from pathlib import Path
import re
import xml.etree.ElementTree as ET
langs = set("en fi sv nb da et lv lt ru fr es it de pl".split())
for module in ("app","tarkastus"):
    root = Path(module)/"src/main/res"
    for lang in sorted(langs):
        path = root/("values" if lang=="fi" else "values-"+lang)/"strings.xml"
        tree = ET.parse(path)
        names = [x.attrib["name"] for x in tree.getroot() if "name" in x.attrib]
        assert len(names)==len(set(names)), str(path)
    print(module, "14 locale files parsed")
base = Path("app/src/main/java/fi/vladi/valvontapoytakirja")
for filename, count in [("CustomReportLabels.kt",36),("CustomPhotoLayout.kt",4),("CustomTemplateTransfer.kt",3)]:
    source = (base/filename).read_text()
    entries = dict(re.findall(r'"([a-z]{2})"\s+to\s+"([^"\n]*\|[^"\n]*)"',source))
    assert set(entries)==langs, (filename,set(entries))
    assert all(len(v.split("|"))==count for v in entries.values()), filename
    print(filename, "14 language entries verified")
print("Static locale checks passed; device UI and PDF rendering require separate tests.")
