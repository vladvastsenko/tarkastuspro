const {createHash} = require("node:crypto");
const accountHash = uid => createHash("sha256").update(uid, "utf8").digest("hex");
const product = "tarkastus_cloud_premium";
function assess(purchase, uid, now = Date.now(), expectedHash = accountHash(uid)) {
  if (purchase.externalAccountIdentifiers?.obfuscatedExternalAccountId !== expectedHash) throw new Error("account mismatch");
  const items = (purchase.lineItems || []).filter(i => i.productId === product && i.offerDetails?.basePlanId === "monthly" && i.autoRenewingPlan);
  if (!items.length) throw new Error("plan mismatch");
  const expiry = Math.max(...items.map(i => Date.parse(i.expiryTime || "") || 0));
  const states = {SUBSCRIPTION_STATE_ACTIVE:"ACTIVE",SUBSCRIPTION_STATE_CANCELED:"CANCELLED",SUBSCRIPTION_STATE_IN_GRACE_PERIOD:"GRACE_PERIOD",SUBSCRIPTION_STATE_PENDING:"PENDING",SUBSCRIPTION_STATE_EXPIRED:"EXPIRED",SUBSCRIPTION_STATE_PAUSED:"PAUSED",SUBSCRIPTION_STATE_ON_HOLD:"ON_HOLD"};
  const state = states[purchase.subscriptionState] || "UNKNOWN";
  const eligible = ["ACTIVE","CANCELLED","GRACE_PERIOD"].includes(state) && expiry > now;
  return {uid,state,expiresAt:expiry,eligible};
}
module.exports = {accountHash,assess,product};
