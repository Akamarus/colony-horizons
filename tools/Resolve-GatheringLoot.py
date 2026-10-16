"""Resolve production loot possibilities without sampling or claiming guaranteed yields.

The output preserves raw referenced tables, path conditions, functions, alternatives,
weights and recipe gates. It is a static over-approximation: mutually incompatible
conditions are not solved, and dynamic/context-generated outcomes remain explicit gaps.
"""
import argparse, hashlib, json, zipfile
from pathlib import Path
from collections import Counter


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--audit', type=Path, default=Path('.setup/minecolonies-audit'))
    parser.add_argument('--output', type=Path, default=Path('docs/minecolonies-map'))
    parser.add_argument('--vanilla-jar', type=Path, required=True, help='Path to the official Minecraft 1.21.1 client jar downloaded by Gradle/launcher')
    args = parser.parse_args()
    jar = zipfile.ZipFile(args.vanilla_jar)
    names = set(jar.namelist())
    runtime = args.audit / 'runtime'
    tables, unresolved, routes = {}, [], []
    unresolved_keys = set()

    def read_resource(path):
        file = runtime / path
        if file.exists():
            return json.loads(file.read_text(encoding='utf-8')), 'runtime/' + path
        if path in names:
            return json.loads(jar.read(path)), args.vanilla_jar.name + '!/' + path
        return None, path

    def issue(kind, value, chain, details=None):
        row = dict(kind=kind, value=value, chain=chain, details=details)
        key = json.dumps(row, sort_keys=True)
        if key not in unresolved_keys:
            unresolved_keys.add(key)
            unresolved.append(row)

    def table(identifier):
        if identifier in tables:
            return tables[identifier]['definition']
        ns, path = identifier.split(':', 1)
        value, source = read_resource(f'data/{ns}/loot_table/{path}.json')
        if value is None:
            return None
        tables[identifier] = dict(id=identifier, source=source, definition=value)
        return value

    def tag_items(identifier, seen=()):
        if identifier in seen:
            return []
        ns, path = identifier.lstrip('#').split(':', 1)
        definition, source = read_resource(f'data/{ns}/tags/item/{path}.json')
        if definition is None:
            issue('missing_item_tag', identifier, list(seen))
            return []
        result = []
        for member in definition.get('values', []):
            member = member.get('id') if isinstance(member, dict) else member
            if member.startswith('#'):
                result.extend(tag_items(member, (*seen, identifier)))
            else:
                result.append(member)
        return result

    def resolve(identifier, chain=(), context=(), seen=()):
        if identifier in seen:
            issue('recursive_table', identifier, list(chain))
            return []
        value = table(identifier)
        if value is None:
            issue('missing_table', identifier, list(chain))
            return []
        return walk(value, identifier, '', (*chain, identifier), context, (*seen, identifier))

    def walk(node, identifier, pointer, chain, context, seen):
        if not isinstance(node, dict):
            return []
        local = {key:node[key] for key in ('conditions', 'functions', 'rolls', 'bonus_rolls', 'weight', 'quality') if key in node}
        local.update(table=identifier, pointer=pointer)
        context = (*context, local)
        typ = node.get('type', '')
        if typ == 'minecraft:item':
            return [dict(item=node['name'], chain=list(chain), context=list(context), entry=node)]
        if typ == 'minecraft:loot_table':
            ref = node.get('value', node.get('name'))
            if isinstance(ref, str):
                return resolve(ref, chain, context, seen)
            if isinstance(ref, dict):
                return walk(ref, identifier, pointer+'/value', chain, context, seen)
            issue('unknown_table_reference', ref, list(chain), node)
            return []
        if typ == 'minecraft:tag':
            return [dict(item=item, chain=list(chain), context=list(context), entry=node) for item in tag_items(node['name'])]
        if typ == 'minecraft:dynamic':
            issue('dynamic_loot', node.get('name'), list(chain), dict(pointer=pointer, context=list(context)))
            return []
        result = []
        for key in ('pools', 'entries', 'children'):
            children = node.get(key, [])
            for index, child in enumerate(children):
                child_context = context
                if key == 'children' and typ in ('minecraft:alternatives','minecraft:sequence'):
                    # Preserve traversal semantics; merely satisfying this child is insufficient.
                    child_context = (*context, dict(composite=typ, preceding_children=children[:index], pointer=pointer))
                result.extend(walk(child, identifier, pointer+f'/{key}/{index}', chain, child_context, seen))
        if typ and typ not in ('minecraft:empty', 'minecraft:alternatives', 'minecraft:sequence', 'minecraft:group', 'minecraft:block', 'minecraft:entity', 'minecraft:fishing', 'minecraft:generic', 'minecraft:chest') and not any(k in node for k in ('pools','entries','children')):
            issue('unhandled_entry_type', typ, list(chain), node)
        return result

    # These functions can change registry ID rather than only stack components/count.
    smelts = {}
    for name in sorted(names):
        if name.startswith('data/minecraft/recipe/') and name.endswith('.json'):
            recipe = json.loads(jar.read(name))
            if recipe.get('type') == 'minecraft:smelting':
                ingredient = recipe.get('ingredient', {})
                result = recipe.get('result', {})
                if isinstance(ingredient, dict) and 'item' in ingredient:
                    smelts[ingredient['item']] = result.get('id') if isinstance(result, dict) else result

    def final_items(outcome):
        possibilities = {outcome['item']}
        for ctx in outcome['context']:
            for function in ctx.get('functions', []):
                fn = function.get('function')
                transformed = set()
                for item in possibilities:
                    if fn in ('minecraft:enchant_randomly','minecraft:enchant_with_levels') and item == 'minecraft:book':
                        transformed.add('minecraft:enchanted_book')
                    elif fn == 'minecraft:exploration_map' and item == 'minecraft:map':
                        transformed.add('minecraft:filled_map')
                    elif fn == 'minecraft:furnace_smelt':
                        transformed.add(smelts.get(item, item))
                    elif fn == 'minecraft:set_item':
                        transformed.add(function.get('item', item))
                    else:
                        transformed.add(item)
                # Conditional functions retain the pre-function possibility.
                possibilities = possibilities | transformed if function.get('conditions') else transformed
        return sorted(possibilities)

    def known_condition(condition, nether):
        if not nether:
            return None
        typ=condition.get('condition')
        if typ in ('minecraft:killed_by_player','minecraft:block_state_property','minecraft:damage_source_properties'):
            return False
        if typ == 'minecraft:entity_properties' and condition.get('entity') in ('attacker','direct_attacker','attacking_player'):
            return False
        if typ == 'minecraft:entity_properties' and condition.get('entity') == 'this':
            predicate=condition.get('predicate',{})
            entity_type=predicate.get('type')
            if isinstance(entity_type,str) and not entity_type.startswith('#') and entity_type != 'minecolonies:citizen':
                return False
            if predicate.get('type_specific',{}).get('type') in ('minecraft:slime','minecraft:frog','minecraft:sheep','minecraft:player'):
                return False
        if typ == 'minecraft:inverted':
            result=known_condition(condition.get('term',{}),nether)
            return None if result is None else not result
        if typ in ('minecraft:all_of','minecraft:any_of'):
            results=[known_condition(x,nether) for x in condition.get('terms',[])]
            if typ == 'minecraft:all_of':
                return False if False in results else (True if all(x is True for x in results) else None)
            return True if True in results else (False if all(x is False for x in results) else None)
        return None

    def emit(worker, kind, gate, evidence, outcome):
        for item in final_items(outcome):
            if item == 'minecolonies:adventure_token':
                issue('unprocessed_adventure_token', item, outcome['chain'])
                continue
            blocked=[condition for ctx in outcome['context'] for condition in ctx.get('conditions',[]) if known_condition(condition,worker=='netherworker') is False]
            availability='context_blocked' if blocked else 'conditional_candidate'
            effective_kind='context_blocked_loot_candidate' if blocked else kind
            routes.append(dict(worker=worker, item=item, route_kind=effective_kind, intended_route_kind=kind, availability=availability, known_unsatisfied_conditions=blocked,
                conditions=dict(availability='conditional possible outcome, not guaranteed; conditions not satisfiability-solved', gates=gate, loot_context=outcome['context']),
                evidence=list(evidence) + outcome['chain'], loot_chain=outcome['chain'], original_entry_item=outcome['item']))

    def add_root(worker, identifier, kind, gate, evidence):
        for outcome in resolve(identifier):
            emit(worker, kind, gate, evidence, outcome)

    fisher_ai='com/minecolonies/core/entity/ai/workers/production/agriculture/EntityAIWorkFisherman.java:678'
    add_root('fisherman','minecolonies:fisherman','renewable_probabilistic_loot', ['Water, rod and fishing AI access; ocean/research treasure gate is retained in loot conditions'], [fisher_ai])
    for level in range(1,6):
        add_root('fisherman',f'minecolonies:fisherman/bonus{level}','renewable_probabilistic_loot', [f'Hut level {level}; rod and accessible water'], [fisher_ai])
        add_root('miner',f'minecolonies:miner/lucky_ore{level}','probabilistic_mining_bonus', [f'Hut level {level}; successful lucky-block mining roll; configurable chance; trigger mining may be finite'], ['com/minecolonies/core/entity/ai/workers/production/EntityAIStructureMiner.java:995'])

    herd = {'cowboy':['cow','mooshroom','goat'], 'chickenherder':['chicken'], 'shepherd':['sheep'], 'swineherder':['pig'], 'rabbitherder':['rabbit']}
    for worker, animals in herd.items():
        for animal in animals:
            add_root(worker,'minecraft:entities/'+animal,'conditional_animal_loot', ['Existing animal; renewable only with breeding pair and food; butchering enabled; tool and entity conditions; cooked outcomes require matching fire condition'], ['com/minecolonies/core/entity/ai/workers/production/herders/AbstractEntityAIHerder.java:346'])

    # Sheep overrides its entity loot table by fleece color; the base table has only meat.
    for name in sorted(names):
        if name.startswith('data/minecraft/loot_table/entities/sheep/') and name.endswith('.json'):
            color=Path(name).stem
            add_root('shepherd','minecraft:entities/sheep/'+color,'conditional_animal_loot', [f'Existing sheep with {color} fleece; unsheared state for wool kill loot; breeding food; butchering enabled'], ['com/minecolonies/core/entity/ai/workers/production/herders/AbstractEntityAIHerder.java:346', 'minecraft sheep color-specific loot-table selection'])
    for wool in tag_items('minecraft:wool'):
        emit('shepherd','renewable_animal_shearing', ['Existing sheep of matching color, not already sheared; grass access to regrow wool; shearing enabled; shears; color-changing dyes are separate inputs'], ['com/minecolonies/core/entity/ai/workers/production/herders/EntityAIWorkShepherd.java:110'], dict(item=wool,chain=['minecraft:wool item tag'],context=[],entry={}))

    for crop in ['wheat','carrots','potatoes','beetroots','melon','pumpkin']:
        add_root('farmer','minecraft:blocks/'+crop,'renewable_cultivation', ['Assigned field and starter crop; mature/valid crop; hoe; block state/light/water conditions'], ['com/minecolonies/core/entity/ai/workers/production/agriculture/EntityAIWorkFarmer.java:780'])
    for file in sorted((runtime/'data/minecolonies/crafterrecipes').rglob('*.json')):
        recipe = json.loads(file.read_text(encoding='utf-8'))
        loot = recipe.get('loot-table')
        if not loot:
            continue
        worker = recipe.get('crafter', file.parent.name)
        gate = {k:recipe[k] for k in ('inputs','min-building-level','max-building-level','research-id','excluded-research-id','intermediate') if k in recipe}
        gate['recipe_source'] = file.relative_to(runtime).as_posix()
        outcomes = resolve(loot)
        for outcome in outcomes:
            if worker != 'netherworker_custom':
                emit(worker,'custom_recipe_probabilistic_loot',gate,[gate['recipe_source']],outcome)
                continue
            original = outcome['item']
            base_evidence=[gate['recipe_source'],'com/minecolonies/core/entity/ai/workers/production/EntityAIWorkNether.java:496']
            if original == 'minecolonies:adventure_token':
                adventures=[]
                for ctx in outcome['context']:
                    for function in ctx.get('functions',[]):
                        adventure=function.get('components',{}).get('minecolonies:adventure')
                        if adventure:
                            adventures.append(adventure)
                if not adventures:
                    issue('adventure_without_entity',original,outcome['chain'],outcome)
                for adventure in adventures:
                    entity=adventure.get('entity')
                    if not entity:
                        issue('adventure_without_entity',adventure,outcome['chain'])
                        continue
                    ns,path=entity.split(':',1)
                    new_context=(*outcome['context'],dict(expedition_processing='Simulated mob combat; survives encounter; correct entity/tool loot context required',adventure=adventure))
                    for final in resolve(f'{ns}:entities/{path}',outcome['chain'],new_context):
                        emit('netherworker','conditional_expedition_entity_loot',gate,base_evidence,final)
            else:
                ns,path=original.split(':',1)
                block_table=f'{ns}:blocks/{path}'
                if table(block_table) is not None:
                    new_context=(*outcome['context'],dict(expedition_processing='Block token converted through block loot for available TieredItem tool; tool wear; Silk Touch/Fortune/state conditions apply',token=original))
                    for final in resolve(block_table,outcome['chain'],new_context):
                        emit('netherworker','conditional_expedition_block_loot',gate,base_evidence+['com/minecolonies/core/entity/ai/workers/production/EntityAIWorkNether.java:516'],final)
                else:
                    # Registry dump distinguishes non-blocks from blocks missing a loot table.
                    emit('netherworker','conditional_expedition_item_loot',gate,base_evidence,outcome)

    args.output.mkdir(parents=True,exist_ok=True)
    # Deduplicate fully identical route objects only: distinct conditional paths matter.
    unique={json.dumps(row,sort_keys=True):row for row in routes}
    routes=sorted(unique.values(),key=lambda r:(r['worker'],r['item'],json.dumps(r['conditions'],sort_keys=True)))
    (args.output/'loot-routes.json').write_text(json.dumps(routes,indent=2)+'\n',encoding='utf-8')
    metadata=dict(schema_version=1, minecraft='1.21.1', minecolonies='1.1.1403-1.21.1',
        vanilla_jar=str(args.vanilla_jar.resolve()), vanilla_jar_sha256=hashlib.sha256(args.vanilla_jar.read_bytes()).hexdigest(),
        interpretation='Conditional candidate outputs; no sampling, probability arithmetic or condition satisfiability solution. Function transformations preserve raw definitions. Tables can reference equipment/state/player requirements absent in particular worker loot contexts.',
        context_warning='Nether getLootContext() uses worker as THIS_ENTITY and supplies no block state, last-damage player or attackers (AbstractEntityAICrafting.java:748-779). Proven unsatisfied conditions are labelled context_blocked and must not seed production coverage. Other satisfiability is not solved.',
        scope='Fishing plus bonuses, lucky mining, known farmer vanilla crops, herder entity loot, all custom recipe loot including sifter/enchanter and Nether transformations. Hut-break and raid loot excluded.',
        tables=[tables[k] for k in sorted(tables)], unresolved=unresolved,
        counts=dict(routes=len(routes), unique_items=len({r['item'] for r in routes}), tables=len(tables), unresolved=len(unresolved), context_blocked_routes=sum(r['availability']=='context_blocked' for r in routes), actionable_candidate_items=len({r['item'] for r in routes if r['availability']!='context_blocked'}), by_worker=dict(Counter(r['worker'] for r in routes))))
    (args.output/'loot-tables.json').write_text(json.dumps(metadata,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(metadata['counts'],indent=2))
    for gap in unresolved:
        print('UNRESOLVED',gap['kind'],gap['value'])

if __name__ == '__main__':
    main()
