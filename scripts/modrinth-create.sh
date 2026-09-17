#!/usr/bin/env bash
# One-time: creates the Modrinth project as a draft. Needs MODRINTH_TOKEN with "Create projects" + "Write projects".
set -euo pipefail
: "${MODRINTH_TOKEN:?set MODRINTH_TOKEN}"
cd "$(dirname "$0")/.."
DESC=$(python3 -c "import json;print(json.load(open('src/client/resources/fabric.mod.json'))['description'])")
python3 - "$DESC" > /tmp/notedown-project.json <<'PY'
import json, sys
print(json.dumps({
    "slug": "notedown",
    "title": "Notedown",
    "description": sys.argv[1],
    "body": open("docs/modrinth.md").read(),
    "categories": ["utility"],
    "client_side": "required",
    "server_side": "unsupported",
    "project_type": "mod",
    "license_id": "MIT",
    "source_url": "https://github.com/BlazeMV/notedown",
    "issues_url": "https://github.com/BlazeMV/notedown/issues",
    "initial_versions": [],
    "is_draft": True,
}))
PY
curl -fsS -X POST "https://api.modrinth.com/v2/project" \
  -H "Authorization: $MODRINTH_TOKEN" \
  -H "User-Agent: BlazeMV/notedown setup script" \
  -F "data=</tmp/notedown-project.json;type=application/json" \
  -F "icon=@src/client/resources/assets/notedown/icon.png;type=image/png" \
  | python3 -c "import json,sys; p=json.load(sys.stdin); print('created', p['id'], 'https://modrinth.com/mod/' + p['slug'], 'status', p['status'])"
rm -f /tmp/notedown-project.json
