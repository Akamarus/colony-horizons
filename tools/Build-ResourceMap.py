"""Generate version-pinned resource catalogues; never infer autonomy from a recipe."""
import csv, json, re, hashlib, argparse
from pathlib import Path
from collections import defaultdict, Counter

def compact(value):
    return json.dumps(value, ensure_ascii=True, separators=(',', ':'))

def table(path, rows, columns):
    with path.open('w', encoding='utf-8-sig', newline='') as f:
        writer=csv.DictWriter(f, fieldnames=columns, extrasaction='ignore')
        writer.writeheader()
        for row in rows:
            writer.writerow({k:compact(v) if isinstance(v,(list,dict)) else v for k,v in row.items() if k in columns})

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--audit',type=Path,default=Path('.setup/minecolonies-audit'))
    p.add_argument('--runtime',type=Path,default=Path('build/reports/resource-audit/runtime-registry.json'))
    p.add_argument('--output',type=Path,default=Path('docs/minecolonies-map'))
    args=p.parse_args(); out=args.output; out.mkdir(parents=True,exist_ok=True)
    root=json.loads(args.runtime.read_text(encoding='utf-8')); data=args.audit/'runtime/data/minecolonies'
    assert root['minecolonies']=='1.1.1403-1.21.1'
    assert not root['candidate_errors'],root['candidate_errors']
    assert not root.get('inherent_ai_errors',[]),root.get('inherent_ai_errors')
    assert len({r['id'] for r in root['items']})==len(root['items'])
    custom=[]
    for path in sorted((data/'crafterrecipes').rglob('*.json')):
        value=json.loads(path.read_text(encoding='utf-8'))
        custom.append({'id':'minecolonies:'+path.relative_to(data/'crafterrecipes').with_suffix('').as_posix(),
          'source':path.relative_to(args.audit/'runtime').as_posix(), 'crafter':value.get('crafter',''),
          'result':value.get('result',{}), 'inputs':value.get('inputs',[]),
          'min_level':value.get('min-building-level','default'), 'max_level':value.get('max-building-level','default'),
          'research':value.get('research-id',[]), 'excluded_research':value.get('excluded-research-id',[]),
          'intermediate':value.get('intermediate',''), 'loot_table':value.get('loot-table',''),
          'additional_output':value.get('additional-output',[]),'complete_definition':value})
    table(out/'custom-recipes.csv',custom,['id','crafter','result','inputs','min_level','max_level','research','excluded_research','intermediate','loot_table','additional_output','source','complete_definition'])
    table(out/'runtime-recipes.csv',root['recipes'],['id','type','serializer','special','result','count','result_stack','ingredients'])
    candidates=root['teachable_candidates']
    table(out/'teachable-recipes.csv',candidates,['module','custom_recipe_key','scope','crafting_type','recipe_id','result','result_stack','inputs'])
    table(out/'crafting-modules.csv',root['crafting_modules'],['module','custom_recipe_key','scope','supported_types'])
    # Preserve all conditions/functions instead of making unsupported probability claims.
    loot=[]
    def walk(value, pointer, context, entries, source):
        if isinstance(value,dict):
            local=context+[{k:value[k] for k in ('conditions','functions','rolls','bonus_rolls') if k in value}]
            local=[x for x in local if x]
            typ=value.get('type','')
            if typ in ('minecraft:item','minecraft:loot_table'):
                entries.append({'table':source,'json_pointer':pointer,'entry_type':typ,'id_or_table':value.get('name',value.get('value','')),'weight':value.get('weight',1),'quality':value.get('quality',0),'context':local,'entry':value})
            for k,v in value.items():walk(v,pointer+'/'+k,local,entries,source)
        elif isinstance(value,list):
            for i,v in enumerate(value):walk(v,pointer+'/'+str(i),context,entries,source)
    for path in sorted((data/'loot_table').rglob('*.json')):
        source='minecolonies:'+path.relative_to(data/'loot_table').with_suffix('').as_posix()
        walk(json.loads(path.read_text(encoding='utf-8')),'',[],loot,source)
    table(out/'loot-entries.csv',loot,['table','json_pointer','entry_type','id_or_table','weight','quality','context','entry'])
    # Job inventory is exact registered entries; names are internal IDs, not UI labels.
    source=args.audit/'source/com/minecolonies'
    ids=dict(re.findall(r'ResourceLocation\s+(\w+)\s*=\s*new ResourceLocation\(Constants.MOD_ID, "([^"]+)"\)',(source/'api/colony/jobs/ModJobs.java').read_text(encoding='utf-8')))
    initializer=(source/'apiimp/initializer/ModJobsInitializer.java').read_text(encoding='utf-8')
    jobs=[]
    for match in re.finditer(r'ModJobs\.(\w+)\s*=\s*register\(DEFERRED_REGISTER, ModJobs\.(\w+)\.getPath\(\),.*?\.setJobProducer\((\w+)::new\)',initializer,re.S):
        jobs.append({'id':'minecolonies:'+ids[match[2]],'field':match[1],'class':match[3],'source':'com/minecolonies/apiimp/initializer/ModJobsInitializer.java:'+str(initializer[:match.start()].count('\n')+1)})
    assert len(jobs)==51,len(jobs)
    table(out/'jobs.csv',jobs,['id','field','class','source'])
    gathering_path=out/'gathering-routes.json'
    gathering=json.loads(gathering_path.read_text(encoding='utf-8')) if gathering_path.exists() else []
    (out/'gathering-routes.json').write_text(json.dumps(gathering,indent=2),encoding='utf-8')
    loot_routes_path=out/'loot-routes.json'
    loot_routes=json.loads(loot_routes_path.read_text(encoding='utf-8')) if loot_routes_path.exists() else []
    routes=defaultdict(list)
    for r in candidates:
        routes[r['result']].append({'kind':'native-compatible-'+r['scope'],'worker':r['custom_recipe_key'],'recipe':r.get('recipe_id',''),'components':r.get('result_stack',{}).get('components',{})})
    for r in custom:
        item=r['result'].get('id') if isinstance(r['result'],dict) else None
        if item:routes[item].append({'kind':'custom-recipe-definition','worker':r['crafter'],'recipe':r['id']})
    # Inherent display entries are deliberately labelled, not promoted to guaranteed production.
    for r in root.get('inherent_ai_display_recipes',[]):
        item=r.get('result')
        if item and item!='minecraft:air':routes[item].append({'kind':'inherent-AI-display','worker':r.get('custom_recipe_key',r.get('module','')),'recipe':r.get('recipe_id','')})
    for route_index,r in enumerate(gathering+loot_routes):
        if r.get('availability')=='context_blocked':continue
        item=r.get('item')
        if item:routes[item].append({'kind':r.get('route_kind','gathering-evidence'),'worker':r.get('worker',''),'detail':('gathering-routes.json#'+str(route_index)) if route_index<len(gathering) else ('loot-routes.json#'+str(route_index-len(gathering)))})
    recipe_outputs=defaultdict(list)
    for r in root['recipes']:
        if r['result']!='minecraft:air':recipe_outputs[r['result']].append(r['id'])
    coverage=[]
    for item in sorted(root['items'],key=lambda x:x['id']):
        evidence=routes[item['id']]
        coverage.append({'item':item['id'],'namespace':item['id'].split(':')[0],'enabled':item['enabled'],
            'status':'route-evidence-see-conditions' if evidence else 'not-resolved-by-this-catalogue',
            'route_kinds':sorted({r['kind'] for r in evidence}),'workers':sorted({r['worker'] for r in evidence}),
            'routes':evidence,'minecraft_recipe_outputs':recipe_outputs[item['id']], 'tags':item['tags']})
    table(out/'item-coverage.csv',coverage,['item','namespace','enabled','status','route_kinds','workers','routes','minecraft_recipe_outputs','tags'])
    # Full native snapshot preserves components and dynamic recipe inputs for later analysis.
    (out/'native-catalogue.json').write_text(json.dumps(root,ensure_ascii=True,separators=(',',':')),encoding='utf-8')
    counts={'items':len(root['items']),'vanilla_items':sum(i['id'].startswith('minecraft:') for i in root['items']),
      'runtime_recipes':len(root['recipes']),'custom_recipe_definitions':len(custom),'job_entries':len(jobs),
      'teachable_candidate_rows':len(candidates),'public_candidate_rows':sum(r['scope']=='public_worker_candidate' for r in candidates),
      'public_candidate_output_ids':len({r['result'] for r in candidates if r['scope']=='public_worker_candidate'}),
      'candidate_errors':len(root['candidate_errors']),'inherent_display_rows':len(root.get('inherent_ai_display_recipes',[])),
      'gathering_evidence_rows':len(gathering),'conditional_loot_route_rows':len(loot_routes),'inherent_export_errors':len(root.get('inherent_ai_errors',[])),'loot_entry_rows':len(loot),
      'recipe_types':dict(Counter(r['type'] for r in root['recipes'])),
      'coverage_status':dict(Counter(r['status'] for r in coverage))}
    provenance={'minecolonies':root['minecolonies'],'minecraft':root['minecraft'],'counts':counts,'artifacts':{}}
    for filename in ('minecolonies.jar','minecolonies-sources.jar'):
        path=args.audit/filename
        provenance['artifacts'][filename]={'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'bytes':path.stat().st_size}
    (out/'manifest.json').write_text(json.dumps(provenance,indent=2),encoding='utf-8')
    print(json.dumps(counts,indent=2))

if __name__=='__main__':main()

