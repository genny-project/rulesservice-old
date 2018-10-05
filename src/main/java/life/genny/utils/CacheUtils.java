package life.genny.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.entity.EntityEntity;
import life.genny.qwanda.entity.SearchEntity;
import life.genny.qwanda.message.QBulkMessage;
import life.genny.qwanda.message.QDataBaseEntityMessage;
import life.genny.qwandautils.JsonUtils;
import life.genny.qwandautils.QwandaUtils;


public class CacheUtils {

	private String realm;

	private BaseEntityUtils baseEntityUtils;
  private Boolean isReloadingCache = false;

	public CacheUtils(String qwandaServiceUrl, String token, Map<String, Object> decodedMapToken, String realm) {
		this.realm = realm;
	}

	public void setBaseEntityUtils(BaseEntityUtils baseEntityUtils) {
		this.baseEntityUtils = baseEntityUtils;
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

	public void refresh(String realm, String cachedItemKey) {
		
		this.reload(realm, cachedItemKey);
	}

	private void reload(String realm, String cachedItemKey) {

    if(this.isReloadingCache == true) return;

    	//this.isReloadingCache = true; // THIS HAS BEEN VOLUNTARELY COMMENTED OUT. DO NOT PUT BACK

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
					this.generateCachedItem(cachedItemKey, cachedItem, token);
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

		this.isReloadingCache = false;
	}
	
	private void generateCachedItem(String cachedItemKey, BaseEntity cachedItem, String token) {
		
		List<QDataBaseEntityMessage> bulkmsg = new ArrayList<QDataBaseEntityMessage>();
		
		try {

			/* we create the searchBE */
			SearchEntity searchBE = new SearchEntity(cachedItemKey, cachedItemKey)
					.addSort("PRI_CREATED", "Created", SearchEntity.Sort.DESC)
					.setSourceCode(cachedItem.getCode())
					.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, "BEG_%")
					.setPageStart(0)
					.setPageSize(10000);
			
			/* we create the searchBE */
			SearchEntity searchBE1 = new SearchEntity(cachedItemKey, cachedItemKey)
					.addSort("PRI_CREATED", "Created", SearchEntity.Sort.DESC)
					.setSourceCode(cachedItem.getCode())
					.addFilter("PRI_CODE", SearchEntity.StringFilter.LIKE, "APP_%")
					.setPageStart(0)
					.setPageSize(10000);

			/* fetching results */
			QDataBaseEntityMessage results = QwandaUtils.fetchResults(searchBE, token);
			QDataBaseEntityMessage results1 = QwandaUtils.fetchResults(searchBE1, token);

			if (results != null) {
				
				/* we grab all the kids */
				results.setParentCode(cachedItem.getCode());
				results.setLinkCode("LNK_CORE");
				bulkmsg.add(results);
				bulkmsg.add(results1);

				/* 2. we loop through each BEG of the current cachedItem */
				for (BaseEntity baseEntity : results.getItems()) {
					List<QDataBaseEntityMessage> baseEntityMessages = this.generateItemCacheHandleBaseEntity(cachedItem.getCode(), baseEntity);
					if(baseEntityMessages != null) {
						bulkmsg.addAll(baseEntityMessages);
					}
					
				}
				
				for (BaseEntity baseEntity : results1.getItems()) {
					List<QDataBaseEntityMessage> baseEntityMessages = this.generateItemCacheHandleBaseEntity(cachedItem.getCode(), baseEntity);
					if(baseEntityMessages != null) {
						bulkmsg.addAll(baseEntityMessages);
					}
				}
				
				QDataBaseEntityMessage cachedItemMessage = new QDataBaseEntityMessage(cachedItem);
				cachedItemMessage.setParentCode(cachedItemKey);
				bulkmsg.add(cachedItemMessage);
			}

		} 
		catch (Exception e) {
			System.out.println("Error generating cached item: " + cachedItem.getCode());
		}

		/* we save the message in cache */
		QDataBaseEntityMessage[] messages = bulkmsg.toArray(new QDataBaseEntityMessage[0]);
		QBulkMessage bulk = new QBulkMessage(messages.clone());
		VertxUtils.putObject(realm, "CACHE", cachedItem.getCode(), bulk);
	}

	private List<QDataBaseEntityMessage> getMessages(String messageCode, String cachedItemKey) {

		/* we need to find the parent cached item and get the message using the messageCode.
		 * however, there might be some other cached items related to the message we are going to grab,
		 * so we need to recursively loop through from the message to its last kids cached in the same parent.
		 * first step is then to grab the first message, get the linked items, and per each one of these items recursively
		 * call the same method and do the same until we have no kids.
		 */

		QBulkMessage cachedItem = VertxUtils.getObject(realm, "CACHE", cachedItemKey, QBulkMessage.class);
		if(cachedItem != null) {

			List<QDataBaseEntityMessage> sourceMessages = Arrays.asList(cachedItem.getMessages());
			List<QDataBaseEntityMessage> messages = new ArrayList<QDataBaseEntityMessage>();

			for(QDataBaseEntityMessage message: sourceMessages) {

				/* we find all the related messages */
				if(message.getItems() != null && message.getItems().length > 0) {

					/* if the parentCode is the messageCode */
					if(message.getParentCode().equals(messageCode)) {

						/* we recursively get the children */
						List<BaseEntity> bes = Arrays.asList(message.getItems());
						for(BaseEntity be: bes) {

							List<QDataBaseEntityMessage> kidMessages = this.getMessages(be.getCode(), cachedItemKey);
							if(kidMessages != null) {
								messages.addAll(kidMessages);
							}
 						}
					}
					/* if the parentCode is the parent */
					else if(message.getParentCode().equals(cachedItemKey)) {

						/* we loop through the messages to find the messageCode */
						for(BaseEntity be: message.getItems()) {
							if(be.getCode().equals(messageCode)) {
								messages.add(message);
							}
						}
					}
				}
			}

			if(messages.size() > 0) return messages;
		}

		return null;
	}

	private void addMessages(List<QDataBaseEntityMessage> messages, String targetCode) {
		
		/* we add all these messages to the target */
		for(QDataBaseEntityMessage message: messages) {
			this.addMessage(message, targetCode);
		}
		
		/* we re-generate the target */
		String token = RulesUtils.generateServiceToken(this.realm);
		try {
			
			BaseEntity targetEntity = QwandaUtils.getBaseEntityByCode(targetCode, token);
			if(targetEntity != null) {
				
				String json = JsonUtils.toJson(targetEntity);
				VertxUtils.writeCachedJson(targetCode, json);
			}
		} 
		catch (IOException e) {
			System.out.println("Error adding message" + targetCode);
		}
	}

	private Boolean addMessage(QDataBaseEntityMessage message, String targetCode) {

		QBulkMessage target = VertxUtils.getObject(realm, "CACHE", targetCode, QBulkMessage.class);

		if(target == null) {
			target = new QBulkMessage();
		}

		/* we calculate the new target messages */
		List<QDataBaseEntityMessage> targetMessages = new ArrayList<QDataBaseEntityMessage>(Arrays.asList(target.getMessages()));
		targetMessages.add(message);
		target.setMessages(targetMessages.toArray(new QDataBaseEntityMessage[0]));

		/* we write it all to vertx*/
		VertxUtils.putObject(this.realm, "CACHE", targetCode, target);
		return true;
	}

	private Boolean removeMessages(List<QDataBaseEntityMessage> messages, String sourceCode) {

		/* we grabbed the cached items */
		/* QBulkMessage source = VertxUtils.getObject(realm, "CACHE", sourceCode, QBulkMessage.class);

		if(source == null) return false;

		/* we calculate the new source messages 
		List<QDataBaseEntityMessage> sourceMessages = Arrays.asList(source.getMessages());
		List<QDataBaseEntityMessage> newSourceMessages = new ArrayList<QDataBaseEntityMessage>();

		/* we loop through all the messages currently sitting in the source 
		for(QDataBaseEntityMessage message: sourceMessages) {

			Boolean found = false;

			/* we loop through all the messages we want to delete 
			for(QDataBaseEntityMessage toDeleteMessage: messages) {

				/* if the messages are not the same we can keep it 
				if(toDeleteMessage.compareTo(message) == 1) {
					found = true;
				}
			}

			/* if we have not found the message in the list of deleted messages, we can keep it 
			if(found == false) {
				newSourceMessages.add(message);
			}
		}

		source.setMessages(newSourceMessages.toArray(new QDataBaseEntityMessage[0]));

		/* we write to the cache 
		VertxUtils.putObject(this.realm, "CACHE", sourceCode, source); */
		
		QBulkMessage itemMessage = this.reloadItem(sourceCode);
		VertxUtils.putObject(this.realm, "CACHE", sourceCode, itemMessage);
		
		return true;
	}

	public void moveBaseEntity(BaseEntity baseEntity, String sourceCode, String targetCode) {
		
		if(sourceCode != null) {
			
			/* we try to fetch the message from the source */
			List<QDataBaseEntityMessage> messagesToMove = this.getMessages(baseEntity.getCode(), sourceCode);
			if(messagesToMove != null && !messagesToMove.isEmpty()) {

				/* we remove the messages for the BE and the linked baseEntities */
				this.removeMessages(messagesToMove, sourceCode);

				/* we regenerate a message for the BE */
				List<QDataBaseEntityMessage> newMessages = this.generateItemCacheHandleBaseEntity(targetCode, baseEntity);

				/* we add the new messages to the target */
				this.addMessages(newMessages, targetCode);
			}
			else {
				
				/* if we could not get it from the source, we generate a new one and add it to the target */
				List<QDataBaseEntityMessage> messages = this.generateItemCacheHandleBaseEntity(targetCode, baseEntity);
				this.addMessages(messages, targetCode);
			}
		}
		else {

			/* if we could not get it from the source, we generate a new one and add it to the target */
			List<QDataBaseEntityMessage> messages = this.generateItemCacheHandleBaseEntity(targetCode, baseEntity);
			this.addMessages(messages, targetCode);
		}
	}

	public void moveBaseEntity(String beCode, String sourceCode, String targetCode) {

		BaseEntity baseEntity = this.baseEntityUtils.getBaseEntityByCode(beCode);
		if(baseEntity != null) {
			this.moveBaseEntity(baseEntity, sourceCode, targetCode);
		}
	}
	
	private QBulkMessage reloadItem(BaseEntity be) {
		return this.reloadItem(be.getCode());
	}
	
	private QBulkMessage reloadItem(String itemCode) {
		
		QBulkMessage bulkmsg = new QBulkMessage();
		
		/* we grab all the kids */
		List<BaseEntity> begKids = this.baseEntityUtils.getBaseEntitysByParentAndLinkCode3(itemCode, "LNK_BEG", 0, 1000, false);
		List<BaseEntity> appbegKids = this.baseEntityUtils.getBaseEntitysByParentAndLinkCode3(itemCode, "LNK_APP", 0, 1000, false);

		if (begKids != null && begKids.size() > 0) {

			/* we create the base entity message for the kids */
			QDataBaseEntityMessage beMsg = new QDataBaseEntityMessage(begKids.toArray(new BaseEntity[0]), itemCode, "LNK_BEG");
			beMsg.setAliasCode(itemCode);
			beMsg.setParentCode(itemCode);
			bulkmsg.add(beMsg);

			/* we then get the kids of these kids (OFR_ have kids as well for instance) */
			for(BaseEntity kid: begKids) {

				/* we grab all the kids */
				List<BaseEntity> kidKids = this.baseEntityUtils.getLinkedBaseEntities(kid.getCode());

				if (kidKids != null && kidKids.size() > 0) {

					/* we create the base entity message for the kids */
					QDataBaseEntityMessage kidMessage = new QDataBaseEntityMessage(kidKids.toArray(new BaseEntity[0]));
					kidMessage.setAliasCode(kid.getCode());
					kidMessage.setParentCode(kid.getCode());
					bulkmsg.add(kidMessage);
				}
			}		
		}
		
		/* for getting application kids (APP_) with LNK_APP link */
		if(appbegKids != null && appbegKids.size() > 0) {
			
			/* we create the base entity message for the kids */
			QDataBaseEntityMessage beMsg = new QDataBaseEntityMessage(appbegKids.toArray(new BaseEntity[0]), itemCode, "LNK_APP");
			beMsg.setAliasCode(itemCode);
			beMsg.setParentCode(itemCode);
			bulkmsg.add(beMsg);
			
			/* we then get the kids of these kids (OFR_ have kids as well for instance) */
			for(BaseEntity kid: appbegKids) {

				/* we grab all the kids */
				List<BaseEntity> kidKids = this.baseEntityUtils.getLinkedBaseEntities(kid.getCode());

				if (kidKids != null && kidKids.size() > 0) {

					/* we create the base entity message for the kids */
					QDataBaseEntityMessage kidMessage = new QDataBaseEntityMessage(kidKids.toArray(new BaseEntity[0]));
					kidMessage.setAliasCode(kid.getCode());
					kidMessage.setParentCode(kid.getCode());
					bulkmsg.add(kidMessage);
				}
			}
		}
		
		return bulkmsg;
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
		List<QDataBaseEntityMessage> bulkmsg = new ArrayList<>();
		bulkmsg.add(cachedItemMessage);

		/* 4. we cache all the kids of the baseEntity */
		QBulkMessage itemMessage = this.reloadItem(parentCode);
		
		/* this caches all the children of the cachedItem. Example : PRT_%, PER_% APP_% that is linked to the BEG_% with linkcode : LNK_BEG or LNK_APP */
		/* if this is not done, then the children BEs that are linked are not cached and thus not published */
		QBulkMessage itemMessageForCachedItem = this.reloadItem(cachedItem);
		
		bulkmsg.addAll(Arrays.asList(itemMessage.getMessages()));
		bulkmsg.addAll(Arrays.asList(itemMessageForCachedItem.getMessages()));

		/* 5. we cache the message */
		QDataBaseEntityMessage[] cachedItemMessagesArray = bulkmsg.toArray(new QDataBaseEntityMessage[0]);
		QBulkMessage bulkItem = new QBulkMessage(cachedItemMessagesArray.clone());
		VertxUtils.putObject(this.realm, "CACHE", cachedItem.getCode(), bulkItem);

		return bulkmsg;
	}
	
	public QBulkMessage fetchAndSubscribeCachedItemsForStakeholder(String realm, String cachedItemKey, BaseEntity stakeholder, Map<String, List<String>> subscriptions) {
		return fetchAndSubscribeCachedItemsForStakeholder(realm, cachedItemKey, stakeholder, subscriptions, new HashMap<>()); 
	}

	
	public QBulkMessage fetchAndSubscribeCachedItemsForStakeholder(String realm, String cachedItemKey, BaseEntity stakeholder, Map<String, List<String>> subscriptions, Map<String, List<String>> allowedBuckets) {

		QBulkMessage bulk = new QBulkMessage();
		String token = RulesUtils.generateServiceToken(realm);

		QDataBaseEntityMessage cachedItemMessages = VertxUtils.getObject(realm, cachedItemKey, realm, QDataBaseEntityMessage.class);

		if (cachedItemMessages != null) {

			/* we loop through the messages */
			for (BaseEntity message : cachedItemMessages.getItems()) {

				/* we grab cache items for the given message */
				QBulkMessage currentItemMessages = new QBulkMessage();
				currentItemMessages = VertxUtils.getObject(realm, "CACHE", message.getCode(), QBulkMessage.class);
				
				if (currentItemMessages != null) {

					List<QDataBaseEntityMessage> messages = new ArrayList<>();
					messages.addAll(Arrays.asList(currentItemMessages.getMessages()));
					
					BaseEntity parentMessage = VertxUtils.readFromDDT(message.getCode(), true, token);
					QDataBaseEntityMessage currentMessageParent = new QDataBaseEntityMessage(parentMessage);
					messages.add(currentMessageParent);
					
					/* we check if the user can see this bucket */
					if(allowedBuckets != null && allowedBuckets.size() > 0) {
						allowedBuckets.forEach((role, bucketsToBeSent) -> {
							
							if(this.isUserRole(stakeholder, role)) {
								for(String bucket : bucketsToBeSent) {
									if(bucket.equals(message.getCode())) {
										bulk.add(messages);
									}
								}
							}			
						});
					} else {
						bulk.add(messages);
					}
				
					/* we subscribe the user to the required buckets */
					
					if(subscriptions != null && subscriptions.size() > 0) {

						/* we check if we need to subscribe the user to the message */
						subscriptions.forEach((role, bucketsToSubscribe) -> {

							if(this.isUserRole(stakeholder, role)) {
								
								for(String bucket : bucketsToSubscribe) {
									if (bucket.equals(message.getCode()) ) {
										
										/* we subscribe the user */
										VertxUtils.subscribe(realm, message, stakeholder.getCode());
									}
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

	public QBulkMessage oldFetchAndSubscribeCachedItemsForStakeholder(final String realm, final String cachedItemKey, final BaseEntity stakeholder, final Map<String, String> subscriptions) {

		QBulkMessage bulk = new QBulkMessage();

		QDataBaseEntityMessage cachedItemMessages = VertxUtils.getObject(realm, cachedItemKey, realm, QDataBaseEntityMessage.class);

		if (cachedItemMessages != null) {

			/* we loop through the messages */
			for (BaseEntity message : cachedItemMessages.getItems()) {

				/* we grab cache items for the given message */
				QBulkMessage currentItemMessages = new QBulkMessage();
				currentItemMessages = VertxUtils.getObject(realm, "CACHE", message.getCode(), QBulkMessage.class);

				if (currentItemMessages != null) {

					List<QDataBaseEntityMessage> messages = new ArrayList<>();
					messages.addAll(Arrays.asList(currentItemMessages.getMessages()));
					QDataBaseEntityMessage currentMessageParent = new QDataBaseEntityMessage(message);
					messages.add(currentMessageParent);
					
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

			/* if the baseEntity has a company code */
			String companyCode = baseEntity.getValue("PRI_COMPANY_CODE", "");
			String authorCode = baseEntity.getValue("PRI_AUTHOR", "");

			/* we try to see if the stakeholder is associated with a company */
			BaseEntity company = this.baseEntityUtils.getParent(stakeholder.getCode(), "LNK_STAFF");
			Boolean isAssociatedWithCompany = false;
			if(company != null) {
				isAssociatedWithCompany = company.getCode().equals(companyCode);
			}

			isUserAssociatedToBaseEntity = authorCode.equals(stakeholder.getCode()) || isAssociatedWithCompany;
		}

		return isUserAssociatedToBaseEntity;
	}

	public QBulkMessage filterBucketItemsForStakeholder(QBulkMessage newItems, final BaseEntity stakeholder) {

		/* variables */
		QBulkMessage ret = new QBulkMessage();
		HashMap<String, List<BaseEntity>> baseEntityMap = new HashMap<String, List<BaseEntity>>();
		HashMap<String, Boolean> excludedBes = new HashMap<String, Boolean>();
		String token = RulesUtils.generateServiceToken(this.realm);

		/* we loop through every single messages in the bulk message */
		for (QDataBaseEntityMessage message : newItems.getMessages()) {

			/* if the message has a parentCode and if the parentCode is not a GRP_ */
			if (message != null && message.getParentCode() != null) {

				String parentCode = message.getParentCode();
				List<BaseEntity> baseEntityKids = new ArrayList<BaseEntity>();

				if (excludedBes.containsKey(parentCode) == false) {

					/* we loop through the items of the given message */
					for (int i = 0; i < message.getItems().length; i++) {

						BaseEntity item = message.getItems()[i];
						BaseEntity be = VertxUtils.readFromDDT(item.getCode(), true, token);
						String itemCode = be.getCode();

						/* if the BE is a user */
						if (itemCode.startsWith("PER_")) {
							
							/*
							 * we simply add it to the list (note: sensitive attributes will be stripped out
							 * on publish
							 */
							baseEntityKids.add(be);
						}

						/* if it is a BEG */
						else if (itemCode.startsWith("BEG_")) {

							if (message.getParentCode().equals("GRP_NEW_ITEMS") && this.isUserSeller(stakeholder)) {

								/* we check if the user has got any filter on the products to display */
								List<String> productTypeTag = this.baseEntityUtils
										.getBaseEntityAttrValueList(stakeholder, "LNK_PRODUCT_CATEGORY_LIST_TAG");
								if (productTypeTag != null) {

									String begTag = be.getValue("LNK_PRODUCT_CATEGORY_TAG", null);
									if (begTag != null && productTypeTag.contains(begTag)) {
										baseEntityKids.add(be);
									} else {
										excludedBes.put(itemCode, true);
									}
								} else {

									/* if not filter has been selected, we send them all */
									baseEntityKids.add(be);
								}
							} else {

								if (this.isUserAssociatedToBaseEntity(stakeholder, be)) {
									baseEntityKids.add(be);
								} else {
									excludedBes.put(itemCode, true);
								}
							}
						}
						/* if the BE is an offer, we only show the ones that the seller created */
						else if (itemCode.startsWith("OFR_")) {

							if (this.isUserBuyer(stakeholder)) {

								/* we add the offer to the list */
								baseEntityKids.add(be);
							} else {

								/* if user is a seller, we only send offers they created */
								String quoterCode = be.getValue("PRI_QUOTER_CODE", "");
								String applicantCode = be.getValue("PRI_APPLICANT_CODE", "");
								
								if (quoterCode.equals(stakeholder.getCode()) || applicantCode.equals(stakeholder.getCode()) ) {

									/* we add the offer to the list */
									baseEntityKids.add(be);
								}
								else {
									excludedBes.put(itemCode, true);
								}

							}
						}
						/* if the BE is an APP_ , we filter only those applications linked to beg of buyer */
						else if (itemCode.startsWith("APP_")) {
							String begCode = be.getValue("PRI_BEG_CODE", null);
									
							if(begCode != null) {
								
								BaseEntity beg = VertxUtils.readFromDDT(begCode, true, token);
								
								if(beg != null) {
									
									if(isUserAssociatedToBaseEntity(stakeholder, beg)) {
										/* we add the application to the list*/
										baseEntityKids.add(be);
									}
								}
							}
						}
						
						/* if the BE is Load, we only show the ones that the seller created */
						else if (itemCode.startsWith("LOD_")) {
							
							if(this.isUserBuyer(stakeholder)) {
								/* if user is a buyer, we only send loads they created */
								String creatorCode = be.getValue("PRI_AUTHOR", "");
								if(creatorCode.equals(stakeholder.getCode())) {
									baseEntityKids.add(be);									
								}
								else {
									excludedBes.put(itemCode, true);
								}
							}
							else {
								baseEntityKids.add(be);	
							}
						}
						else {

							/* if it is not a BEG */
							if (itemCode.startsWith("BEG_") == false) {

								/* we send the rest */
								baseEntityKids.add(be);
							}
						}
					}

					/* we put in the baseEntityMap the list of kids for the given parentCode */
					List<BaseEntity> existingKids = baseEntityMap.get(parentCode);

					/* (if the list of kids for the parentCode does not exist, we create it) */
					if (existingKids == null) {
						existingKids = new ArrayList<BaseEntity>();
					}

					existingKids.addAll(baseEntityKids);
					baseEntityMap.put(parentCode, existingKids);
				}
				// Had to do this as the 3rd level BE's linked were not being filtered properly
				else if(excludedBes.containsKey(parentCode) == true) {
					/* we loop through the items of the given message */
					for (int i = 0; i < message.getItems().length; i++) {

						BaseEntity itemToExclude = message.getItems()[i];
						String itemCode = itemToExclude.getCode();
						excludedBes.put(itemCode, true);
				   }
			   }
			}
		}

		/*
		 * once we have looped through all the items of this message, we create a
		 * QDataBaseEntityMessage per set of baseEntities
		 */
		baseEntityMap.forEach((parentCode, kids) -> {

			QDataBaseEntityMessage baseEntityMessage = new QDataBaseEntityMessage(kids.toArray(new BaseEntity[0]));
			baseEntityMessage.setParentCode(parentCode);
			ret.add(baseEntityMessage);
		});

		return ret;
	}

	public static List<BaseEntity> getBaseEntityWithChildren(String beCode, Integer level, String token) {

		if (level == 0) {
			return null; // exit point;
		}

		List<BaseEntity> result = new ArrayList<BaseEntity>();

		BaseEntity parent = VertxUtils.readFromDDT(beCode, token);
		result.add(parent);

		for (EntityEntity ee : parent.getLinks()) {
			String childCode = ee.getLink().getTargetCode();
			BaseEntity child = VertxUtils.readFromDDT(childCode, token);
			result.add(child);
		}

		return result;
	}
	
	public static List<BaseEntity> getChildren(String beCode, Integer level, String token) {

		if (level == 0) {
			return null; // exit point;
		}

		List<BaseEntity> result = new ArrayList<BaseEntity>();

		BaseEntity parent = VertxUtils.readFromDDT(beCode, token);

		for (EntityEntity ee : parent.getLinks()) {
			String childCode = ee.getLink().getTargetCode();
			BaseEntity child = VertxUtils.readFromDDT(childCode, token);
			result.add(child);
		}

		return result;
	}
}
