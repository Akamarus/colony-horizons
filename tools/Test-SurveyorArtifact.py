"""Check the installable addon contains survival assets, without development fixtures."""
import argparse
import json
import zipfile

p = argparse.ArgumentParser()
p.add_argument("jar")
args = p.parse_args()
with zipfile.ZipFile(args.jar) as jar:
    names = set(jar.namelist())
    for name in (
        "data/colonyhorizons/recipe/surveyor_hut.json",
        "data/colonyhorizons/loot_table/blocks/blockhutsurveyor.json",
        "assets/colonyhorizons/blockstates/blockhutsurveyor.json",
        "assets/colonyhorizons/models/block/blockhutsurveyor.json",
        "assets/colonyhorizons/models/item/blockhutsurveyor.json",
    ):
        assert name in names, f"Missing production asset: {name}"
        json.loads(jar.read(name))
    for name in ("GatheringService", "GatheringSavedData", "GatheringCargo", "GatheringCatalog", "ExpeditionTravel", "DiscoveryEvidence"):
        assert f"dev/colonyhorizons/surveyor/{name}.class" in names, f"Missing exploration runtime: {name}"
    layout=jar.read("assets/colonyhorizons/gui/supply/surveys.xml").decode()
    assert 'id="gather"' in layout and 'id="repeat"' in layout and 'id="outposts"' not in layout
    assert json.loads(jar.read("assets/colonyhorizons/lang/en_us.json"))["block.colonyhorizons.blockhutsurveyor"]=="Exploration Hut"
    assert not any(n.startswith("dev/colonyhorizons/qa/") for n in names), "QA code shipped"
    assert not any(n.startswith("data/colonyhorizons/structure/") for n in names), "QA structures shipped"
print("PASS Surveyor packaged recipe, loot, models, and QA isolation")
