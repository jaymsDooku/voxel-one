# Voxel One development

## Progress dashboard

The user wants past and current work tracked with testing evidence. Maintain
`dashboard/progress.json` while working. Read only the entries relevant to the task.
Update at task start, significant phase changes or blockers, and completion.
Generally leave at least ten minutes between intermediate updates; task start,
completion and important blockers can be exceptions. Skip unchanged updates.
Do not schedule additional agent runs or repeatedly poll merely to update progress.

Use one short command per milestone:

```sh
python3 deploy/update_progress.py set ITEM_ID --status in_progress --title "New work title" --note "Concrete progress" --publish
python3 deploy/update_progress.py set ITEM_ID --status complete --note "Implemented, verified and deployed" --evidence "Build results|https://github.com/..." --publish
```

Statuses: `queued`, `in_progress`, `blocked`, `complete`. A title is needed only
for new items. Publish requires existing `gh` authentication and network access.
It writes the dedicated `development-progress` branch, without game CI or release
work. Evidence files go directly inside `dashboard/evidence/`; use
`--artifact FILENAME` to upload one and attach its link. Attach actual test reports,
CI results, recorded screenshots or relevant regression sources. Never include
passwords, account databases, TLS private keys, environment dumps or full private
runtime logs. Label historical evidence honestly. Do not invent test results.
Completion requires finishing the requested implementation and relevant checks.

## Website

`website/` contains the landing page, documentation and dashboard. Run
`python3 deploy/build_site.py` after README or website content changes.
`website/.openai/hosting.json` identifies the existing Sites project; reuse it.
Use the Sites skills to publish website changes. Keep its source checkout separate
from the game repository (for example `/tmp/voxel-one-site`) so no nested Git
repository is committed. Progress JSON changes do not need website redeployment.
The dashboard reads the milestone branch on page open, manual refresh, and return
to a tab after five minutes. This requires no agent work or token-consuming polls.
