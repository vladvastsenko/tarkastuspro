import json
from pathlib import Path
p = Path("app/google-services.json")
data = json.loads(p.read_text())
info = data["project_info"]
assert info["project_id"] == "tarkastus-cloud", "Unexpected Firebase project"
assert str(info["project_number"]) == "425303240003", "Unexpected Firebase project number"
clients = [c for c in data["client"] if c["client_info"]["android_client_info"]["package_name"] == "fi.vladi.tarkastuscloud"]
assert len(clients) == 1, "Expected exactly one Cloud Android client"
assert any(o.get("client_type") == 3 for o in clients[0].get("oauth_client", [])), "Missing Google web OAuth client"
print("Cloud Firebase configuration passed static checks")
