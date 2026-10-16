"""Verify the Surveyor's five levels preserve the existing Arcology module geometry."""
import argparse
from pathlib import Path
from arcology_nbt import load

p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--pack',type=Path,required=True);a=p.parse_args()
for level in range(1,6):
    path=a.pack/'fundamentals'/f'surveyor{level}.blueprint'
    assert path.is_file(), f'Missing native Surveyor level {level}'
    source,_,_=load(a.source/'fundamentals'/f'builder{level}.blueprint')
    result,_,_=load(path)
    assert all(source[k]==result[k] for k in ('size_x','size_y','size_z','blocks','optional_data'))
    huts=[v for v in result['tile_entities'] if v.get('id')=='minecolonies:colonybuilding']
    assert len(huts)==1 and huts[0]['type']=='colonyhorizons:surveyor'
    assert huts[0]['colony']==0 and huts[0]['pack']=='' and huts[0]['path']==''
    assert all(v.get('empty')==1 for v in huts[0]['inventory'])
    assert huts[0]['blueprintDataProvider']['schematicName']==f'surveyor{level}'
    for old,new in zip(source['palette'],result['palette'],strict=True):
        expected={**old,'Name':'colonyhorizons:blockhutsurveyor'} if old['Name']=='minecolonies:blockhutbuilder' else old
        assert expected==new, 'Unrelated geometry/material changed'
print('PASS: five Surveyor levels, unchanged Arcology geometry/connectors/materials, clean native hut identity')
