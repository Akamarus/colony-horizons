"""Verify the recipe and Barracks hooks are present in the installable jar."""
import json, sys, zipfile
import xml.etree.ElementTree as E
with zipfile.ZipFile(sys.argv[1]) as jar:
    names=set(jar.namelist())
    config=json.loads(jar.read('colonyhorizons.mixins.json'))
    assert {'RecipeCapacityAccess','BarracksUpgradePreservationMixin'} <= set(config['mixins'])
    assert 'CraftingCatalogWindowMixin' in config['client']
    for name in ('recipes/RecipeCatalog','recipes/RecipeCatalogConfig','network/RecipeCatalogNetwork','client/recipes/RecipeCatalogWindow','client/recipes/CatalogRecipeList','integration/BarracksTowerPreservation'):
        assert f'dev/colonyhorizons/{name}.class' in names,name
    layout=E.fromstring(jar.read('assets/colonyhorizons/gui/recipes/catalog.xml'))
    ids={p.get('id') for p in layout.iter()}
    assert {'query','search','entries','prev','next','learn','manual','back','capacity','output'} <= ids
    assert not any('/qa/' in n for n in names)
print('PASS: recipe catalog, native client entry, Barracks protection and UI assets; no QA code')
