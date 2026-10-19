"""Read XML on stdin; return typed values on stdout for the backup process only."""
import json
import sys
import xml.etree.ElementTree as ET

raw = sys.stdin.buffer.read(16 * 1024 * 1024 + 1)
if len(raw) > 16 * 1024 * 1024 or b'<!DOCTYPE' in raw.upper() or b'<!ENTITY' in raw.upper():
    raise ValueError('Invalid preferences XML')
root = ET.fromstring(raw)
if root.tag != 'map':
    raise ValueError('Expected Android SharedPreferences map')
result = {}
for item in root:
    name = item.attrib['name']
    if name in result:
        raise ValueError('Duplicate preference key')
    if item.tag == 'string':
        value = {'t': 's', 'v': item.text or ''}
    elif item.tag == 'set':
        if any(child.tag != 'string' for child in item):
            raise ValueError('Invalid string set')
        value = {'t': 'set', 'v': sorted(set(child.text or '' for child in item))}
    elif item.tag in ('boolean', 'int', 'long', 'float'):
        value = {'t': {'boolean': 'b', 'int': 'i', 'long': 'l', 'float': 'f'}[item.tag], 'v': item.attrib['value']}
    else:
        raise ValueError('Unsupported preference type')
    result[name] = value
json.dump(result, sys.stdout, ensure_ascii=True, sort_keys=True)
