from pathlib import Path
import xml.etree.ElementTree as E
import json
root=Path(__file__).resolve().parents[1]
base=root/'src/main/resources/assets/colonyhorizons/gui/supply'
required={'main':{'outposts','routes','entries','details','new','pause','stop','back','prev','next','message'},'outpost':{'name','entries','review','create','back','details','prev','next','message'},'route':{'entries','source','destination','target','reserve','advanced','batch','create','back','details','prev','next','message'}}
required.update({'founding':{'title','hint','entries','name','x','y','z','allowance','reserve','mode','cap','pageStock','previous','continue','prev','next','back','message'},'mayor':{'title','details','entries','view','refresh','pause','back','message'}})
required['upgrade']={'title','details','entries','approve','reject','back','message'}
required['surveys']={'title','details','status','entries','start','cancel','findings','gather','repeat','back','message'}
for name,ids in required.items():
 tree=E.parse(base/f'{name}.xml'); found={p.get('id') for p in tree.iter()}; assert ids<=found,(name,ids-found)
 text=(base/f'{name}.xml').read_text();assert 'lightbox="true"' in text
config=json.loads((root/'src/supplyDev/resources/colonyhorizons.mixins.json').read_text())
assert 'TownHallSupplyMenuMixin' not in config['client']
assert 'TownHallSupplyMenuMixin' not in config['mixins']
lang=json.loads((root/'src/main/resources/assets/colonyhorizons/lang/en_us.json').read_text())
assert lang['colonyhorizons.menu.stop_confirm']=='Stop future shipments and return cargo already in transit.'
assert len([p for p in E.parse(base/'main.xml').iter() if p.get('id') in ['outposts','routes']])==2
assert 'surveys' in {p.get('id') for p in E.parse(base/'surveyor-tab.xml').iter()}
print('PASS: menu layouts, native widgets, Surveyor tab assets and stop confirmation')

assert 'id="outposts"' not in (base/"surveys.xml").read_text()
