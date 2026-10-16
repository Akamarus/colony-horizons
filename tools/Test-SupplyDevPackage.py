"""Check the experimental courier artifact boundary, not the published alpha."""
import json
import sys
import zipfile
import argparse
parser=argparse.ArgumentParser()
parser.add_argument("jar")
parser.add_argument("--version", default="0.1.0-dev.menus.1")
args=parser.parse_args()

required = {
    "CourierColonyMarkerMixin", "CourierRequestResolverMixin", "CourierJobMixin",
    "CourierWorkMixin", "CourierNavigationMixin",
    "WarehouseRequestLocalityMixin", "WarehouseCountLocalityMixin", "RemoteDeliveredFoodMixin", "RemoteIllnessMixin",
}
failures = []
with zipfile.ZipFile(args.jar) as jar:
    names = set(jar.namelist())
    config = json.loads(jar.read("colonyhorizons.mixins.json"))
    missing = required - set(config["mixins"])
    if missing:
        failures.append("Missing runtime hooks: " + ", ".join(sorted(missing)))
    for hook in config["mixins"]:
        if config["package"].replace(".", "/") + "/" + hook + ".class" not in names:
            failures.append("Missing hook class: " + hook)
    for name in ("CourierDistrictPolicy", "NativeCourierDistricts", "NativeCourierGuard", "NativeCourierCustody", "ManagedCourierColony", "CourierDeliveryState", "SupplyFileWriter", "WarehouseInventory"):
        if "dev/colonyhorizons/supply/" + name + ".class" not in names:
            failures.append("Missing production class: " + name)
    for name in names:
        if "/qa/" in name or "FoundationTestSaveMixin" in name or "SupplyCheckpointTimingMixin" in name:
            failures.append("Test code in artifact: " + name)
        if name.endswith(".class") and b"dev/colonyhorizons/qa/" in jar.read(name):
            failures.append("Test reference in artifact: " + name)
        if name.endswith((".blueprint", ".nbt", ".jar")):
            failures.append("Bundled external/test asset: " + name)
    for name in ("dev/colonyhorizons/ColonyHorizons.class", "META-INF/neoforge.mods.toml", "LICENSE", "NOTICE"):
        if name not in names:
            failures.append("Missing entrypoint/metadata/notice: " + name)
    if "dev/colonyhorizons/surveyor/SurveyorRegistration.class" in names:
        if "TownHallSupplyMenuMixin" in config.get("client", []):
            failures.append("Outpost entry should be at the Surveyor hut")
        for name in ("surveyor/SurveyorMenuModule", "surveyor/SurveyorMenuView", "client/supply/WindowSurveyorTab", "client/supply/WindowSurveys"):
            if "dev/colonyhorizons/"+name+".class" not in names:
                failures.append("Missing Surveyor menu class: "+name)
    elif "TownHallSupplyMenuMixin" not in config.get("client", []):
        failures.append("Missing legacy client-only Town Hall hook")
    for name in ("menu/SupplyMenuSessions", "network/SupplyMenuPayloads", "client/supply/WindowOutpostsSupplies", "client/supply/WindowRemoteFounding", "client/supply/WindowMayor", "menu/RemoteMenuService", "remote/RemoteCare", "remote/RemoteExpansion", "remote/ExpansionProposal", "mayor/MayorProposals", "mayor/MayorUpgrades", "client/supply/WindowUpgradeReview"):
        if "dev/colonyhorizons/"+name+".class" not in names:
            failures.append("Missing menu class: "+name)
    for layout in ("main", "outpost", "route", "founding", "mayor", "upgrade"):
        if "assets/colonyhorizons/gui/supply/"+layout+".xml" not in names:
            failures.append("Missing menu layout: "+layout)
    metadata = jar.read("META-INF/neoforge.mods.toml").decode()
    if args.version not in metadata or "-dev." not in args.version:
        failures.append("Artifact must have a distinct development version")
if failures:
    print("\n".join("FAIL: " + failure for failure in failures))
    sys.exit(1)
print("PASS: five courier hooks, production classes and notices; distinct dev version; no QA references/assets")
