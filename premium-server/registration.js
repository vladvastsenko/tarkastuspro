const {createHash,randomBytes} = require("node:crypto");
const hash = value => createHash("sha256").update(value,"utf8").digest("hex");
const secret = () => randomBytes(32).toString("hex");
const validSecret = value => typeof value === "string" && /^[a-f0-9]{64}$/.test(value);
function assertPurchase(purchase, session, now = Date.now()) {
  if (!validSecret(session)) throw new Error("invalid session");
  if (purchase.externalAccountIdentifiers?.obfuscatedExternalAccountId !== hash(session)) throw new Error("account mismatch");
  const {assess} = require("./policy");
  const result = assess(purchase,session,now);
  if (!result.eligible || result.state !== "ACTIVE") throw new Error("purchase not active");
  return result;
}
async function issuePermit(db, token, session, purchase, now = Date.now()) {
  const result = assertPurchase(purchase,session,now);
  const permit = require("node:crypto").createHmac("sha256",session).update("registration:"+token).digest("hex");
  const claim = db.collection("premiumRegistrationClaims").doc(hash(token));
  const ref = db.collection("premiumRegistrationPermits").doc(hash(permit));
  await db.runTransaction(async tx => {
    const existing = await tx.get(claim);
    if (existing.exists) {
      const c = existing.data();
      if (c.sessionHash !== hash(session)) throw new Error("purchase already claimed");
      if (c.permitHash === hash(permit)) return;
      if (c.uid) throw new Error("purchase already claimed");
      const old = await tx.get(db.collection("premiumRegistrationPermits").doc(c.permitHash));
      if (old.data()?.expiresAt > now && !old.data()?.used) throw new Error("permit still active");
      tx.update(claim,{permitHash:hash(permit)});
    } else tx.create(claim,{sessionHash:hash(session),permitHash:hash(permit),createdAt:now});
    tx.create(ref,{claimId:claim.id,sessionHash:hash(session),expiresAt:Math.min(now+600000,result.expiresAt),used:false});
  });
  return permit;
}
async function consumePermit(db, permit, session, uid, now = Date.now()) {
  if (!validSecret(permit) || !validSecret(session) || typeof uid !== "string" || !uid) throw new Error("invalid permit");
  const ref = db.collection("premiumRegistrationPermits").doc(hash(permit));
  return db.runTransaction(async tx => {
    const snap = await tx.get(ref);
    const p = snap.data();
    if (!p || p.used || p.expiresAt <= now || p.sessionHash !== hash(session)) throw new Error("permit unavailable");
    const claim = db.collection("premiumRegistrationClaims").doc(p.claimId);
    const c = (await tx.get(claim)).data();
    if (!c || c.uid || c.permitHash !== hash(permit)) throw new Error("claim unavailable");
    tx.update(ref,{used:true,uid,usedAt:now});
    tx.update(claim,{uid,boundAt:now});
    return p.claimId;
  });
}
module.exports = {hash,secret,assertPurchase,issuePermit,consumePermit};

/** Reserve a stable server-owned UID before calling Firebase Auth. */
async function reserveAccount(db, permit, session, email, now = Date.now(), verifiedUntil = 0) {
  if (!validSecret(permit) || !validSecret(session) || typeof email !== "string" || !email || email.length > 254) throw new Error("invalid registration");
  const ref = db.collection("premiumRegistrationPermits").doc(hash(permit));
  return db.runTransaction(async tx => {
    const p = (await tx.get(ref)).data();
    if (!p || p.sessionHash !== hash(session)) throw new Error("permit unavailable");
    const claim = db.collection("premiumRegistrationClaims").doc(p.claimId);
    const c = (await tx.get(claim)).data();
    if (!c || c.sessionHash !== hash(session) || c.permitHash !== hash(permit)) throw new Error("claim unavailable");
    const emailHash = hash(email.trim().toLowerCase());
    if (c.uid) {
      if (!p.used || c.emailHash !== emailHash || p.uid !== c.uid) throw new Error("claim unavailable");
      return c.uid;
    }
    if (p.used || (p.expiresAt <= now && verifiedUntil <= now)) throw new Error("permit unavailable");
    const uid = "premium_" + secret();
    tx.update(ref,{used:true,uid,usedAt:now});
    tx.update(claim,{uid,emailHash,boundAt:now});
    return uid;
  });
}
module.exports.reserveAccount = reserveAccount;
