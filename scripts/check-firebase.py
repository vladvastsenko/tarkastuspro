import json
from pathlib import Path
p = Path("app/google-services.json")
data = json.loads(p.read_text())
info = data["project_info"]
assert info["project_id"] != "tarkastus-cloud", "Pro must use independent Firebase project"
clients = [c for c in data["client"] if c["client_info"]["android_client_info"]["package_name"] == "fi.vladi.tarkastuspro"]
assert len(clients) == 1, "Expected exactly one Pro Android client"
assert any(o.get("client_type") == 3 for o in clients[0].get("oauth_client", [])), "Missing Google web OAuth client"
print("Pro Firebase configuration passed static checks")
