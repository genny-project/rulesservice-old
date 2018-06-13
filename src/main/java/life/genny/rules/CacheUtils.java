package life.genny.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.entity.SearchEntity;
import life.genny.qwanda.message.QBulkMessage;
import life.genny.qwanda.message.QDataBaseEntityMessage;
import life.genny.qwandautils.QwandaUtils;
import life.genny.utils.VertxUtils;

public class CacheUtils {

	private Map<String, Object> decodedMapToken;
	private String token;
	private String realm;
	private String qwandaServiceUrl;

	public BaseEntityUtils baseEntityUtils;

	public CacheUtils(String qwandaServiceUrl, String token, Map<String, Object> decodedMapToken, String realm) {

		this.decodedMapToken = decodedMapToken;
		this.qwandaServiceUrl = qwandaServiceUrl;
		this.token = token;
		this.realm = realm;

		this.baseEntityUtils = new BaseEntityUtils(this.qwandaServiceUrl, this.token, decodedMapToken, realm);
	}

	/* TODO: to remove */
	public Boolean isUserRole(BaseEntity user, String role) {

		Boolean isRole = false;

		Object uglyRole = user.getValue(role, null);
		if (uglyRole instanceof Boolean) {
			isRole = user.is(role);
		} else {
			String uglyRoleString = (String) uglyRole;
			isRole = "TRUE".equalsIgnoreCase(uglyRoleString);
		}

		return isRole;
	}

	public Boolean isUserBuyer(BaseEntity user) {
		return this.isUserRole(user, "PRI_IS_BUYER");
	}

	public Boolean isUserSeller(BaseEntity user) {
		return !this.isUserBuyer(user);
	}

	/*     */ 

	public void refresh(String realm, String cachedItemKey) {

		/* we generate a service token */
		String token = RulesUtils.generateServiceToken(realm);

		System.out.println("Generating message for cached item: " + cachedItemKey);


		/* we grab the cached Item */
		QDataBaseEntityMessage cachedItemMessages = VertxUtils.getObject(realm, cachedItemKey, realm, QDataBaseEntityMessage.class);

		if (cachedItemMessages != null) {

			/* we loop through each bucket to cache the messages */
			for (BaseEntity cachedItem : cachedItemMessages.getItems()) {

				List<QDataBaseEntityMessage> bulkmsg = new ArrayList<QDataBaseEntityMessage>();

				if(cachedItem.getCode().startsWith("GRP_")) {

					try {

						/* we create the searchBE */
						SearchEntity searchBE = new SearchEntity(cachedItemKey, cachedItemKey)
								.addSort("PRI_CREATED", "Created", SearchEntity.Sort.DESC)
								.setSourceCode(cachedItem.getCode())
								.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, "BEG_%")
								.setPageStart(0)
								.setPageSize(10000);

						/* fetching results */
						QDataBaseEntityMessage results = QwandaUtils.fetchResults(searchBE, token);

						if (results != null) {

							results.setParentCode(cachedItem.getCode());
							results.setLinkCode("LNK_CORE");
							bulkmsg.add(results);

							/* 2. we loop through each BEG of the current cachedItem */
							for (BaseEntity baseEntity : results.getItems()) {
								List<QDataBaseEntityMessage> baseEntityMessages = this.generateItemCacheHandleBaseEntity(cachedItem.getCode(), baseEntity);
								if(baseEntityMessages != null) {
									bulkmsg.addAll(baseEntityMessages);
								}
							}
						}

					} catch (Exception e) {
						e.printStackTrace();
					}

					/* we save the message in cache */
					QDataBaseEntityMessage[] messages = bulkmsg.toArray(new QDataBaseEntityMessage[0]);
					QBulkMessage bulk = new QBulkMessage(messages.clone());
					VertxUtils.putObject(realm, "CACHE", cachedItem.getCode(), bulk);
				}
				/* if it is a BEG */
				else {

					List<QDataBaseEntityMessage> baseEntityMessages = this.generateItemCacheHandleBaseEntity(cachedItemMessages.getParentCode(), cachedItem);
					if(baseEntityMessages != null) {
						bulkmsg.addAll(baseEntityMessages);
					}

					/* we save the message in cache */
					QDataBaseEntityMessage[] messages = bulkmsg.toArray(new QDataBaseEntityMessage[0]);
					QBulkMessage bulk = new QBulkMessage(messages.clone());
					VertxUtils.putObject(realm, "CACHE", cachedItemMessages.getParentCode(), bulk);
				}
			}
		}
	}

	private List<QDataBaseEntityMessage> generateItemCacheHandleBaseEntity(String parentCode, BaseEntity cachedItem) {

		/* 1. we add the cached item to the message "items" */
		BaseEntity[] cachedItemItems = new BaseEntity[1];
		cachedItemItems[0] = cachedItem;

		/* 2. we create the data baseentity message containing the array above */
		QDataBaseEntityMessage cachedItemMessage = new QDataBaseEntityMessage(cachedItemItems, parentCode, "LNK_CORE");
		cachedItemMessage.setAliasCode(parentCode);
		cachedItemMessage.setParentCode(parentCode);

		/* 3. we add it to the bulk message */
		List<QDataBaseEntityMessage> bulkmsg = new ArrayList<QDataBaseEntityMessage>();
		bulkmsg.add(cachedItemMessage);

		/* 4. we cache all the kids of the baseEntity */

		/* we grab all the kids */
		List<BaseEntity> begKids = this.baseEntityUtils.getBaseEntitysByParentAndLinkCode3(cachedItem.getCode(), "LNK_BEG", 0, 1000, false);

		if (begKids != null) {

			/* we create the base entity message for the kids */
			QDataBaseEntityMessage beMsg = new QDataBaseEntityMessage(begKids.toArray(new BaseEntity[0]), cachedItem.getCode(), "LNK_BEG");
			beMsg.setAliasCode(cachedItem.getCode());
			beMsg.setParentCode(cachedItem.getCode());
			bulkmsg.add(beMsg);

			/* we then get the kids of these kids (OFR_ have kids as well for instance) */
			for(BaseEntity kid: begKids) {

				/* we grab all the kids */
				List<BaseEntity> kidKids = this.baseEntityUtils.getLinkedBaseEntities(kid.getCode());

				if (kidKids != null) {

					/* we create the base entity message for the kids */
					QDataBaseEntityMessage kidMessage = new QDataBaseEntityMessage(kidKids.toArray(new BaseEntity[0]));
					kidMessage.setAliasCode(kid.getCode());
					kidMessage.setParentCode(kid.getCode());
					bulkmsg.add(kidMessage);
				}
			}
		}

		/* 6. we cache the message */
		QDataBaseEntityMessage[] cachedItemMessagesArray = bulkmsg.toArray(new QDataBaseEntityMessage[0]);
		QBulkMessage bulkItem = new QBulkMessage(cachedItemMessagesArray.clone());
		VertxUtils.putObject(this.realm, "CACHE", cachedItem.getCode(), bulkItem);

		return bulkmsg;
	}

	public QBulkMessage fetchAndSubscribeCachedItemsForStakeholder(final String realm, final String cachedItemKey, final BaseEntity stakeholder, final Map<String, String> subscriptions) {

		QBulkMessage bulk = new QBulkMessage();

		QDataBaseEntityMessage cachedItemMessages = VertxUtils.getObject(realm, cachedItemKey, realm, QDataBaseEntityMessage.class);

		if (cachedItemMessages != null) {

			/* we loop through the messages */
			for (BaseEntity message : cachedItemMessages.getItems()) {

				/* we grab cache items for the given message */
				QBulkMessage currentItemMessages = new QBulkMessage();
				currentItemMessages = VertxUtils.getObject(realm, "CACHE", message.getCode(), QBulkMessage.class);

				if (currentItemMessages != null) {

					QDataBaseEntityMessage[] messages = currentItemMessages.getMessages();

					/* we add it to the list of items to send */
					bulk.add(messages);

					if(subscriptions != null) {

						/* we check if we need to subscribe the user to the message */
						subscriptions.forEach((role, bucketToSubscribe) -> {

							if(this.isUserRole(stakeholder, role)) {

								if (bucketToSubscribe.equals(message.getCode()) ) {

									/* we subscribe the user */
									VertxUtils.subscribe(realm, message, stakeholder.getCode());
								}
							}
						});
					}
				}
			}
		}

		/* return the bulk message */
		return bulk;
	}

	/*
	 * @param user
	 * @param baseEntity
	 */
	private Boolean isUserAssociatedToBaseEntity(BaseEntity stakeholder, BaseEntity baseEntity) {

		Boolean isUserAssociatedToBaseEntity = false;

		if(this.isUserSeller(stakeholder)) {

			/* we send BEGs only where the seller is a stakeholder */
			String sellerCode = baseEntity.getValue("PRI_SELLER_CODE", "");
			isUserAssociatedToBaseEntity = sellerCode.equals(stakeholder.getCode());
		}
		else if(this.isUserBuyer(stakeholder)) {

			/* we send BEGs only the buyer created */
			String authorCode = baseEntity.getValue("PRI_AUTHOR", "");
			isUserAssociatedToBaseEntity = authorCode.equals(stakeholder.getCode());
		}

		return isUserAssociatedToBaseEntity;
	}

	public QBulkMessage filterBucketItemsForStakeholder(QBulkMessage newItems, final BaseEntity stakeholder) {

		/* variables */
		QBulkMessage ret = new QBulkMessage();
		HashMap<String, List<BaseEntity>> baseEntityMap = new HashMap<String, List<BaseEntity>>();
		HashMap<String, Boolean> excludedBes = new HashMap<String, Boolean>();

		/* we loop through every single messages in the bulk message */
		for (QDataBaseEntityMessage message : newItems.getMessages()) {

			/* if the message has a parentCode and if the parentCode is not a GRP_ */
			if (message.getParentCode() != null) {

				String parentCode = message.getParentCode();
				List<BaseEntity> baseEntityKids = new ArrayList<BaseEntity>();

				if(excludedBes.containsKey(parentCode) == false) {

					/* we loop through the items of the given message */
					for (int i = 0; i < message.getItems().length; i++) {

						BaseEntity item = message.getItems()[i];
						String itemCode = item.getCode();

						/* if the BE is a user */
						if(itemCode.startsWith("PER_")) {

							/* we simply add it to the list (note: sensitive attributes will be stripped out on publish */
							baseEntityKids.add(item);
						}

						/* if it is a BEG */
						else if(itemCode.startsWith("BEG_")) {

							if(message.getParentCode().equals("GRP_NEW_ITEMS") && this.isUserSeller(stakeholder)) {
								baseEntityKids.add(item);
							}
							else {

								if(this.isUserAssociatedToBaseEntity(stakeholder, item)) {
									baseEntityKids.add(item);
								}
								else {
									excludedBes.put(itemCode, true);
								}
							}
						}
						/* if the BE is an offer, we only show the ones that the seller created */
						else if(itemCode.startsWith("OFR_")) {

							if(this.isUserBuyer(stakeholder)) {

								/* we add the offer to the list */
								baseEntityKids.add(item);
							}
							else {

								/* if user is a seller, we only send offers they created */
								String quoterCode = item.getValue("PRI_QUOTER_CODE", "");
								if(quoterCode.equals(stakeholder.getCode())) {

									/* we add the offer to the list */
									baseEntityKids.add(item);
								}
							}
						}
						else {

							/* if it is not a BEG */
							if(itemCode.startsWith("BEG_") == false) {

								/* we send the rest */
								baseEntityKids.add(item);
							}
						}
					}

					/* we put in the baseEntityMap the list of kids for the given parentCode */
					List<BaseEntity> existingKids = baseEntityMap.get(parentCode);

					/* (if the list of kids for the parentCode does not exist, we create it) */
					if(existingKids == null) {
						existingKids = new ArrayList<BaseEntity>();
					}

					existingKids.addAll(baseEntityKids);
					baseEntityMap.put(parentCode, existingKids);
				}
			}
		}

		/* once we have looped through all the items of this message, we create a QDataBaseEntityMessage per set of baseEntities */
		baseEntityMap.forEach((parentCode, kids) -> {

			QDataBaseEntityMessage baseEntityMessage = new QDataBaseEntityMessage(kids.toArray(new BaseEntity[0]));
			baseEntityMessage.setParentCode(parentCode);
			ret.add(baseEntityMessage);
		});

		return ret;
	}

}
