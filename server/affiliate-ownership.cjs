'use strict';
// Commission attribution boundary: no fallback to public aggregators or another user's URLs.
const PLATFORM = Object.freeze({SHOPEE:'shopee', TIKTOK:'tiktok_shop'});
const LINK_STATUS = Object.freeze({VERIFIED:'verified_owner', UNSUPPORTED:'unverified'});
function isOwnedConnection(c, userId, platform) {
  return Boolean(userId && c && c.ownerUserId === userId &&
    c.platform === platform && c.status === 'connected' &&
    c.verifiedAffiliateAccountId && c.capabilities?.affiliateLinks === true);
}
function authorizeAffiliateLink({userId, connection, product, generatedLink}) {
  if (!isOwnedConnection(connection,userId,product?.platform))
    throw new Error('affiliate_connection_not_authorized');
  if (!product?.productId || !generatedLink?.url || !generatedLink?.attributedAffiliateAccountId)
    throw new Error('official_affiliate_link_required');
  if (generatedLink.attributedAffiliateAccountId !== connection.verifiedAffiliateAccountId)
    throw new Error('affiliate_account_mismatch');
  const url = new URL(generatedLink.url);
  if (url.protocol !== 'https:') throw new Error('invalid_link_protocol');
  const hosts = product.platform===PLATFORM.SHOPEE?
    ['shopee.com.br','s.shopee.com.br','shope.ee']:
    ['tiktok.com','shop.tiktok.com'];
  const domainAllowed=hosts.some(h=>url.hostname===h || url.hostname.endsWith('.'+h));
  if(!domainAllowed)throw new Error('unexpected_affiliate_link_domain');
  // Never accept affiliate identity from the untrusted Android client.
  return Object.freeze({platform:product.platform, productId:product.productId,
    affiliateAccountId:connection.verifiedAffiliateAccountId, ownerUserId:userId,
    url:url.href, attribution:LINK_STATUS.VERIFIED, source:'official_account_api'});
}
function canPublish(offer) {
  return Boolean(offer && offer.attribution === LINK_STATUS.VERIFIED &&
    offer.source === 'official_account_api' && offer.ownerUserId && offer.affiliateAccountId);
}
module.exports={PLATFORM,LINK_STATUS,isOwnedConnection,authorizeAffiliateLink,canPublish};
