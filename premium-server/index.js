const {onCall,HttpsError} = require("firebase-functions/v2/https");
const {GoogleAuth} = require("google-auth-library");
const {assess,product} = require("./policy");
const auth = new GoogleAuth({scopes:["https://www.googleapis.com/auth/androidpublisher"]});
const base = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/fi.vladi.tarkastuscloud/purchases";
exports.verifyPremium = onCall({region:"europe-west1",maxInstances:5}, async request => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated","Sign in required");
  const token = request.data?.purchaseToken;
  if (typeof token !== "string" || token.length < 8 || token.length > 4096) throw new HttpsError("invalid-argument","Invalid token");
  try {
    const client = await auth.getClient();
    const {data} = await client.request({url:base+"/subscriptionsv2/tokens/"+encodeURIComponent(token),timeout:15000});
    const claim = (await getFirestore().collection("premiumRegistrationClaims").doc(hash(token)).get()).data();
    if (claim && claim.uid !== uid) throw new Error("purchase binding mismatch");
    const result = claim
      ? assess(data,uid,Date.now(),claim.sessionHash)
      : assess(data,uid);
    if (result.eligible && data.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING") {
      await client.request({method:"POST",url:base+"/subscriptions/"+product+"/tokens/"+encodeURIComponent(token)+":acknowledge",data:{},timeout:15000});
    } else if (result.eligible && data.acknowledgementState !== "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED") {
      throw new Error("Unknown acknowledgement state");
    }
    return {...result,expiresAt:Math.min(result.expiresAt,Date.now()+5*60*1000)};
  } catch (_) {
    // Never expose purchase tokens or upstream credentials in errors.
    throw new HttpsError("failed-precondition","Subscription could not be verified");
  }
});

const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {issuePermit,hash} = require("./registration");
initializeApp();
exports.preparePremiumRegistration = onCall({region:"europe-west1",maxInstances:5,enforceAppCheck:true}, async request => {
  const {purchaseToken,session} = request.data || {};
  if (typeof purchaseToken !== "string" || purchaseToken.length < 8 || purchaseToken.length > 4096 ||
      typeof session !== "string" || !/^[a-f0-9]{64}$/.test(session))
    throw new HttpsError("invalid-argument","Invalid registration request");
  try {
    const client = await auth.getClient();
    const {data} = await client.request({url:base+"/subscriptionsv2/tokens/"+encodeURIComponent(purchaseToken),timeout:15000});
    const permit = await issuePermit(getFirestore(),purchaseToken,session,data);
    return {permit};
  } catch (_) {
    throw new HttpsError("failed-precondition","Registration permit unavailable");
  }
});

 // Deploy only after the paid registration flow is ready.
const {beforeUserCreated} = require("firebase-functions/v2/identity");
exports.blockDirectRegistration = beforeUserCreated({region:"europe-west1"}, () => {
  throw new HttpsError("permission-denied","Registration requires a verified purchase");
});

const {getAuth} = require("firebase-admin/auth");
const {reserveAccount,assertPurchase} = require("./registration");
exports.completePremiumEmailRegistration = onCall({region:"europe-west1",maxInstances:5,enforceAppCheck:true}, async request => {
  const {purchaseToken,session,permit,email,password} = request.data || {};
  if (typeof purchaseToken !== "string" || purchaseToken.length < 8 || purchaseToken.length > 4096 ||
      typeof email !== "string" || email.length > 254 || !email.includes("@") ||
      typeof password !== "string" || password.length < 8 || password.length > 128 ||
      typeof permit !== "string" || !/^[a-f0-9]{64}$/.test(permit))
    throw new HttpsError("invalid-argument","Invalid registration request");
  try {
    const db = getFirestore();
    const client = await auth.getClient();
    const {data} = await client.request({url:base+"/subscriptionsv2/tokens/"+encodeURIComponent(purchaseToken),timeout:15000});
    const verified = assertPurchase(data,session);
    const p = (await db.collection("premiumRegistrationPermits").doc(hash(permit)).get()).data();
    if (!p || p.claimId !== hash(purchaseToken)) throw new Error("purchase mismatch");
    const address = email.trim().toLowerCase();
    const admin = getAuth();
    const claim = (await db.collection("premiumRegistrationClaims").doc(hash(purchaseToken)).get()).data();
    const existingEmail = await admin.getUserByEmail(address).catch(e => {
      if (e.code === "auth/user-not-found") return null;
      throw e;
    });
    if (existingEmail && existingEmail.uid !== claim?.uid)
      throw new HttpsError("already-exists","Email already registered");
    const uid = await reserveAccount(db,permit,session,address,Date.now(),Math.min(verified.expiresAt,Date.now()+60000));
    try {
      await admin.createUser({uid,email:address,password,emailVerified:false});
    } catch (e) {
      if (e.code !== "auth/uid-already-exists" && e.code !== "auth/email-already-exists") throw e;
      const existing = await admin.getUser(uid);
      if (existing.email?.toLowerCase() !== address) throw new Error("account mismatch");
    }
    if (data.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING") {
      await client.request({method:"POST",url:base+"/subscriptions/"+product+"/tokens/"+encodeURIComponent(purchaseToken)+":acknowledge",data:{},timeout:15000});
    } else if (data.acknowledgementState !== "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED") throw new Error("unknown acknowledgement");
    return {created:true};
  } catch (_) {
    if (_ instanceof HttpsError) throw _;
    throw new HttpsError("failed-precondition","Registration could not be completed");
  }
});

 // Existing Google identities only; never create or link accounts by email.
const {OAuth2Client} = require("google-auth-library");
const {defineString} = require("firebase-functions/params");
const googleAudience = defineString("GOOGLE_WEB_CLIENT_ID");
const googleVerifier = new OAuth2Client();
exports.signInExistingGoogle = onCall({region:"europe-west1",maxInstances:5,enforceAppCheck:true}, async request => {
  const token = request.data?.idToken;
  if (typeof token !== "string" || token.length > 8192 || !token)
    throw new HttpsError("invalid-argument","Invalid Google credential");
  try {
    const ticket = await googleVerifier.verifyIdToken({idToken:token,audience:googleAudience.value()});
    const identity = ticket.getPayload();
    if (!identity?.sub || identity.email_verified !== true) throw new Error("Unverified identity");
    const admin = getAuth();
    const lookup = await admin.getUsers([{providerId:"google.com",providerUid:identity.sub}]);
    const user = lookup.users.find(u => u.providerData.some(p => p.providerId === "google.com" && p.uid === identity.sub));
    if (!user) return {registrationRequired:true};
    if (user.disabled) throw new Error("Disabled account");
    return {customToken:await admin.createCustomToken(user.uid)};
  } catch (_) {
    throw new HttpsError("permission-denied","Existing Google account required");
  }
});

exports.completePremiumGoogleRegistration = onCall({region:"europe-west1",maxInstances:5,enforceAppCheck:true}, async request => {
  const {purchaseToken,session,permit,idToken} = request.data || {};
  if (typeof purchaseToken !== "string" || purchaseToken.length < 8 || purchaseToken.length > 4096 ||
      typeof permit !== "string" || !/^[a-f0-9]{64}$/.test(permit) ||
      typeof idToken !== "string" || !idToken || idToken.length > 8192)
    throw new HttpsError("invalid-argument","Invalid registration request");
  try {
    const identity = (await googleVerifier.verifyIdToken({idToken,audience:googleAudience.value()})).getPayload();
    if (!identity?.sub || !identity.email || identity.email_verified !== true) throw new Error("Unverified identity");
    const address = identity.email.trim().toLowerCase();
    const db = getFirestore(), admin = getAuth(), client = await auth.getClient();
    const {data} = await client.request({url:base+"/subscriptionsv2/tokens/"+encodeURIComponent(purchaseToken),timeout:15000});
    const verified = assertPurchase(data,session);
    const p = (await db.collection("premiumRegistrationPermits").doc(hash(permit)).get()).data();
    if (!p || p.claimId !== hash(purchaseToken)) throw new Error("purchase mismatch");
    const claim = (await db.collection("premiumRegistrationClaims").doc(hash(purchaseToken)).get()).data();
    const lookup = await admin.getUsers([{providerId:"google.com",providerUid:identity.sub},{email:address}]);
    if (lookup.users.some(u => u.uid !== claim?.uid)) throw new HttpsError("already-exists","Account already exists");
    const uid = await reserveAccount(db,permit,session,"google:"+identity.sub,Date.now(),Math.min(verified.expiresAt,Date.now()+60000));
    let user;
    try {
      user = await admin.createUser({uid,email:address,emailVerified:true});
    } catch (e) {
      if (e.code !== "auth/uid-already-exists" && e.code !== "auth/email-already-exists") throw e;
      user = await admin.getUser(uid);
    }
    if (user.disabled || user.email?.toLowerCase() !== address) throw new Error("Account mismatch");
    if (!user.providerData.some(p => p.providerId === "google.com" && p.uid === identity.sub)) {
      if (user.providerData.length) throw new Error("Provider mismatch");
      user = await admin.updateUser(uid,{providerToLink:{providerId:"google.com",uid:identity.sub,email:address}});
    }
    if (!user.providerData.some(p => p.providerId === "google.com" && p.uid === identity.sub)) throw new Error("Provider not linked");
    if (data.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING") {
      await client.request({method:"POST",url:base+"/subscriptions/"+product+"/tokens/"+encodeURIComponent(purchaseToken)+":acknowledge",data:{},timeout:15000});
    } else if (data.acknowledgementState !== "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED") throw new Error("Unknown acknowledgement");
    return {customToken:await admin.createCustomToken(uid)};
  } catch (e) {
    if (e instanceof HttpsError) throw e;
    throw new HttpsError("failed-precondition","Google registration could not be completed");
  }
});
