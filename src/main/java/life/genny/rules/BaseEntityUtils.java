package life.genny.rules;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.Logger;

import com.google.gson.reflect.TypeToken;

import life.genny.qwanda.Answer;
import life.genny.qwanda.Layout;
import life.genny.qwanda.Link;
import life.genny.qwanda.attribute.Attribute;
import life.genny.qwanda.attribute.AttributeText;
import life.genny.qwanda.attribute.EntityAttribute;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.entity.EntityEntity;
import life.genny.qwanda.exception.BadDataException;
import life.genny.qwanda.message.QDataAnswerMessage;
import life.genny.qwanda.message.QDataBaseEntityMessage;
import life.genny.qwandautils.JsonUtils;
import life.genny.qwandautils.QwandaUtils;
import life.genny.utils.VertxUtils;

public class BaseEntityUtils {

	
	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	

	private Map<String, Object> decodedMapToken;
	private String token;
	private String realm;
	private String qwandaServiceUrl;
	
	private CacheUtils cacheUtil;

	public BaseEntityUtils(String qwandaServiceUrl, String token, Map<String, Object> decodedMapToken, String realm) {

  	this.decodedMapToken = decodedMapToken;
		this.qwandaServiceUrl = qwandaServiceUrl;
		this.token = token;
		this.realm = realm;
		
		this.cacheUtil = new CacheUtils(qwandaServiceUrl, token, decodedMapToken, realm);
		this.cacheUtil.setBaseEntityUtils(this);
	}

  /* =============== refactoring =============== */


  public BaseEntity create(final String uniqueCode, final String bePrefix, final String name) {

    String uniqueId = QwandaUtils.getUniqueId(uniqueCode, null, bePrefix, this.token);
    if (uniqueId != null) {

      BaseEntity newBaseEntity = QwandaUtils.createBaseEntityByCode(uniqueId, name, qwandaServiceUrl, this.token);
      this.addAttributes(newBaseEntity);
      VertxUtils.writeCachedJson(newBaseEntity.getCode(), JsonUtils.toJson(newBaseEntity));
      return newBaseEntity;
    }

    return null;
  }

  

  /*================================ */
  /* old code */


  public BaseEntity createRole(final String uniqueCode, final String name, String ... capabilityCodes) {
	  String code = "ROL_"+uniqueCode.toUpperCase();
	  log.info("Creating Role "+code+":"+name);
	  BaseEntity role = this.getBaseEntityByCode(code);
	  if (role==null) {
	      role = QwandaUtils.createBaseEntityByCode(code, name, qwandaServiceUrl, this.token);
	      this.addAttributes(role);
	      
	      VertxUtils.writeCachedJson(role.getCode(), JsonUtils.toJson(role));
	  }
	  
	  for (String capabilityCode : capabilityCodes) {
		  Attribute capabilityAttribute = RulesUtils.attributeMap.get("CAP_"+capabilityCode);
		  try {
			role.addAttribute(capabilityAttribute,1.0,"TRUE");
		} catch (BadDataException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	  }
	  
	  // Now force the role to only have these capabilitys
		try {
			String result = QwandaUtils.apiPutEntity(qwandaServiceUrl + "/qwanda/baseentitys/force", JsonUtils.toJson(role), this.token);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	  
	  return role;
	  }

  
	public Object get(final String key) {
		return this.decodedMapToken.get(key);
	}

	public void set(final String key, Object value) {
		this.decodedMapToken.put(key, value);
	}

	
	public Attribute saveAttribute(Attribute attribute, final String token) throws IOException
	{
		
		RulesUtils.attributeMap.put(attribute.getCode(), attribute);
		try {
			String result = QwandaUtils.apiPostEntity(this.qwandaServiceUrl + "/qwanda/attributes", JsonUtils.toJson(attribute), token);
			return attribute;
		} catch (IOException e) {
			log.error("Socket error trying to post attribute");
			throw new IOException("Cannot save attribute");
		}


	}
	

	public void addAttributes(BaseEntity be) {

		if (be != null) {

			if (!be.getCode().startsWith("SBE_")) { // don't bother with search be
				for (EntityAttribute ea : be.getBaseEntityAttributes()) {
					if (ea != null) {
						Attribute attribute = RulesUtils.attributeMap.get(ea.getAttributeCode());
						if (attribute != null) {
							ea.setAttribute(attribute);
						} else {
							RulesUtils.loadAllAttributesIntoCache(this.token);
							attribute = RulesUtils.attributeMap.get(ea.getAttributeCode());
							if (attribute != null) {
								ea.setAttribute(attribute);
							} else {
								System.out.println("Cannot get Attribute - " + ea.getAttributeCode());
								Attribute dummy = new AttributeText(ea.getAttributeCode(), ea.getAttributeCode());
								ea.setAttribute(dummy);

							}
						}
					}
				}
			}
		}
	}

	public void saveAnswer(Answer answer) {

		try {
			this.updateCachedBaseEntity(answer);
			QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/answers", JsonUtils.toJson(answer), this.token);
			// Now update the Cache

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void saveAnswers(List<Answer> answers, final boolean changeEvent) {

		if (!changeEvent) {
			for (Answer answer : answers) {
				answer.setChangeEvent(false);
			}
		}
		Answer items[] = new Answer[answers.size()];
		items = answers.toArray(items);

		QDataAnswerMessage msg = new QDataAnswerMessage(items);

		this.updateCachedBaseEntity(answers);

		String jsonAnswer = JsonUtils.toJson(msg);
		jsonAnswer.replace("\\\"", "\"");

		try {
			QwandaUtils.apiPostEntity(this.qwandaServiceUrl + "/qwanda/answers/bulk2", jsonAnswer, token);
		} catch (IOException e) {
			//log.error("Socket error trying to post answer");
		}
	}

	public void saveAnswers(List<Answer> answers) {
		this.saveAnswers(answers, true);
	}


	public BaseEntity getOfferBaseEntity(String groupCode, String linkCode, String linkValue, String quoterCode,
			String token) {

		List linkList = this.getLinkList(groupCode, linkCode, linkValue, token);
		String quoterCodeForOffer = null;

		if (linkList != null) {

			try {

				for (Object linkObj : linkList) {

					Link link = JsonUtils.fromJson(linkObj.toString(), Link.class);

					BaseEntity offerBe = this.getBaseEntityByCode(link.getTargetCode());

					if (offerBe != null) {

						quoterCodeForOffer = offerBe.getValue("PRI_QUOTER_CODE", null);

						if (quoterCode.equals(quoterCodeForOffer)) {
							return offerBe;
						}
					}
				}
			}
			catch(Exception e) {

			}
		}

		return null;
	}

	public void updateBaseEntityAttribute(final String sourceCode, final String beCode, final String attributeCode,
			final String newValue) {
		List<Answer> answers = new ArrayList<Answer>();
		answers.add(new Answer(sourceCode, beCode, attributeCode, newValue));
		this.saveAnswers(answers);
	}

	public BaseEntity getBaseEntityByCode(final String code) {
		return this.getBaseEntityByCode(code, true);
	}

	public BaseEntity getBaseEntityByCode(final String code, Boolean withAttributes) {

		BaseEntity be = null;

		try {

			be = VertxUtils.readFromDDT(code, withAttributes, this.token);
			if (be == null) {
				System.out.println("ERROR - be (" + code + ") fetched is NULL ");
			} else {
				this.addAttributes(be);
			}
		}
		catch(Exception e) {
			System.out.println("Failed to read cache for " + code);
		}

		return be;
	}

	public BaseEntity getBaseEntityByAttributeAndValue(final String attributeCode, final String value) {

		BaseEntity be = null;
		be = RulesUtils.getBaseEntityByAttributeAndValue(this.qwandaServiceUrl, this.decodedMapToken, this.token, attributeCode, value);
		this.addAttributes(be);
		return be;
	}

	public List<BaseEntity> getBaseEntitysByAttributeAndValue(final String attributeCode, final String value) {

		List<BaseEntity> bes = null;
		bes = RulesUtils.getBaseEntitysByAttributeAndValue(this.qwandaServiceUrl, this.decodedMapToken, this.token, attributeCode, value);
		return bes;
	}

	public void clearBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode, Integer pageStart,
			Integer pageSize) {

		String key = parentCode + linkCode + "-" + pageStart + "-" + pageSize;
		VertxUtils.putObject(this.realm, "LIST", key, null);
	}

	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode,
			Integer pageStart, Integer pageSize, Boolean cache) {
		cache = false;
		List<BaseEntity> bes = new ArrayList<BaseEntity>();
		String key = parentCode + linkCode + "-" + pageStart + "-" + pageSize;
		if (cache) {
			Type listType = new TypeToken<List<BaseEntity>>() {
			}.getType();
			List<String> beCodes = VertxUtils.getObject(this.realm, "LIST", key, (Class) listType);
			if (beCodes == null) {
				bes = RulesUtils.getBaseEntitysByParentAndLinkCodeWithAttributes(qwandaServiceUrl, this.decodedMapToken,
						this.token, parentCode, linkCode, pageStart, pageSize);
				beCodes = new ArrayList<String>();
				for (BaseEntity be : bes) {
					VertxUtils.putObject(this.realm, "", be.getCode(), JsonUtils.toJson(be));
					beCodes.add(be.getCode());
				}
				VertxUtils.putObject(this.realm, "LIST", key, beCodes);
			} else {
				for (String beCode : beCodes) {
					BaseEntity be = getBaseEntityByCode(beCode);
					bes.add(be);
				}
			}
		} else {

			bes = RulesUtils.getBaseEntitysByParentAndLinkCodeWithAttributes(qwandaServiceUrl, this.decodedMapToken,
					this.token, parentCode, linkCode, pageStart, pageSize);
		}

		return bes;
	}

	/* added because of the bug */
	public List<BaseEntity> getBaseEntitysByParentAndLinkCode2(final String parentCode, final String linkCode,
			Integer pageStart, Integer pageSize, Boolean cache) {

		List<BaseEntity> bes = null;

		// if (isNull("BES_" + parentCode.toUpperCase() + "_" + linkCode)) {

		bes = RulesUtils.getBaseEntitysByParentAndLinkCodeWithAttributes2(qwandaServiceUrl, this.decodedMapToken,
				this.token, parentCode, linkCode, pageStart, pageSize);

		// } else {
		// bes = getAsBaseEntitys("BES_" + parentCode.toUpperCase() + "_" + linkCode);
		// }

		return bes;
	}

	public List<BaseEntity> getBaseEntitysByParentLinkCodeAndLinkValue(final String parentCode, final String linkCode,
			final String linkValue, Integer pageStart, Integer pageSize, Boolean cache) {

		List<BaseEntity> bes = null;

		bes = RulesUtils.getBaseEntitysByParentAndLinkCodeAndLinkValueWithAttributes(qwandaServiceUrl,
				this.decodedMapToken, this.token, parentCode, linkCode, linkValue, pageStart, pageSize);
		return bes;
	}

	public List<BaseEntity> getBaseEntitysByParentAndLinkCode(final String parentCode, final String linkCode,
			Integer pageStart, Integer pageSize, Boolean cache, final String stakeholderCode) {
		List<BaseEntity> bes = null;

		bes = RulesUtils.getBaseEntitysByParentAndLinkCodeWithAttributesAndStakeholderCode(qwandaServiceUrl,
				this.decodedMapToken, this.token, parentCode, linkCode, stakeholderCode);
		if (cache) {
			set("BES_" + parentCode.toUpperCase() + "_" + linkCode, bes); // WATCH THIS!!!
		}

		return bes;
	}

	public String moveBaseEntity(final String baseEntityCode, final String sourceCode, final String targetCode,
			final String linkCode) {
		
		Link link = new Link(sourceCode, baseEntityCode, linkCode);
		
		try {
			
			/* we call the api */
			QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/move/" + targetCode,
					JsonUtils.toJson(link), this.token);
			
			/* we refresh the cache */
			//this.cacheUtil.moveBaseEntity(baseEntityCode, sourceCode, targetCode);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}

	public String moveBaseEntitySetLinkValue(final String baseEntityCode, final String sourceCode,
			final String targetCode, final String linkCode, final String linkValue) {

		Link link = new Link(sourceCode, baseEntityCode, linkCode, linkValue);

		try {

			/* we call the api */
			QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/baseentitys/move/" + targetCode,
					JsonUtils.toJson(link), this.token);
			
			/* we refresh the cache */
			//this.cacheUtil.moveBaseEntity(baseEntityCode, sourceCode, targetCode);

		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	public Object getBaseEntityValue(final String baseEntityCode, final String attributeCode) {
		BaseEntity be = getBaseEntityByCode(baseEntityCode);
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);
		if (ea.isPresent()) {
			return ea.get().getObject();
		} else {
			return null;
		}
	}

	public static String getBaseEntityAttrValueAsString(BaseEntity be, String attributeCode) {

		String attributeVal = null;
		for (EntityAttribute ea : be.getBaseEntityAttributes()) {
			try {
				if (ea.getAttributeCode().equals(attributeCode)) {
					attributeVal = ea.getObjectAsString();
				}
			} catch (Exception e) {
			}
		}

		return attributeVal;
	}

	public String getBaseEntityValueAsString(final String baseEntityCode, final String attributeCode) {

		String attrValue = null;

		if (baseEntityCode != null) {

			BaseEntity be = getBaseEntityByCode(baseEntityCode);
			attrValue = getBaseEntityAttrValueAsString(be, attributeCode);
		}

		return attrValue;
	}

	public LocalDateTime getBaseEntityValueAsLocalDateTime(final String baseEntityCode, final String attributeCode) {
		BaseEntity be = getBaseEntityByCode(baseEntityCode);
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);
		if (ea.isPresent()) {
			return ea.get().getValueDateTime();
		} else {
			return null;
		}
	}

	public LocalDate getBaseEntityValueAsLocalDate(final String baseEntityCode, final String attributeCode) {
		BaseEntity be = getBaseEntityByCode(baseEntityCode);
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);
		if (ea.isPresent()) {
			return ea.get().getValueDate();
		} else {
			return null;
		}
	}

	public LocalTime getBaseEntityValueAsLocalTime(final String baseEntityCode, final String attributeCode) {

		BaseEntity be = getBaseEntityByCode(baseEntityCode);
		Optional<EntityAttribute> ea = be.findEntityAttribute(attributeCode);
		if (ea.isPresent()) {
			return ea.get().getValueTime();
		} else {
			return null;
		}
	}

	public BaseEntity getParent(final String targetCode, final String linkCode) {
		List<BaseEntity> parents = this.getParents(targetCode, linkCode);
		if (parents != null && parents.size() > 0) {
			return parents.get(0);
		}

		return null;
	}

	public List<BaseEntity> getParents(final String targetCode, final String linkCode) {
		List<BaseEntity> parents = null;
		long sTime = System.nanoTime();
		try {

			String beJson = QwandaUtils.apiGet(this.qwandaServiceUrl + "/qwanda/entityentitys/" + targetCode
					+ "/linkcodes/" + linkCode + "/parents", this.token);
			Link[] linkArray = JsonUtils.fromJson(beJson, Link[].class);
			if (linkArray.length > 0) {

				ArrayList<Link> arrayList = new ArrayList<Link>(Arrays.asList(linkArray));
				parents = new ArrayList<BaseEntity>();
				for (Link lnk : arrayList) {

					BaseEntity linkedBe = getBaseEntityByCode(lnk.getSourceCode());
					if (linkedBe != null) {
						parents.add(linkedBe);
					}
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		double difference = (System.nanoTime() - sTime) / 1e6; // get ms
		return parents;
	}

	public List<EntityEntity> getLinks(BaseEntity be) {
		return this.getLinks(be.getCode());
	}

	public List<EntityEntity> getLinks(String beCode) {

		List<EntityEntity> links = new ArrayList<EntityEntity>();
		BaseEntity be = this.getBaseEntityByCode(beCode);
		if (be != null) {

			Set<EntityEntity> linkSet = be.getLinks();
			links.addAll(linkSet);
		}

		return links;
	}

	public BaseEntity getLinkedBaseEntity(String beCode, String linkCode, String linkValue) {

		List<BaseEntity> bes = this.getLinkedBaseEntities(beCode, linkCode, linkValue);
		if(bes != null && bes.size() > 0) {
			return bes.get(0);
		}

		return null;
	}

	public List<BaseEntity> getLinkedBaseEntities(BaseEntity be) {
		return this.getLinkedBaseEntities(be.getCode(), null, null);
	}

	public List<BaseEntity> getLinkedBaseEntities(String beCode) {
		return this.getLinkedBaseEntities(beCode, null, null);
	}

	public List<BaseEntity> getLinkedBaseEntities(BaseEntity be, String linkCode) {
		return this.getLinkedBaseEntities(be.getCode(), linkCode, null);
	}

	public List<BaseEntity> getLinkedBaseEntities(String beCode, String linkCode) {
		return this.getLinkedBaseEntities(beCode, linkCode, null);
	}

	public List<BaseEntity> getLinkedBaseEntities(BaseEntity be, String linkCode, String linkValue) {
		return this.getLinkedBaseEntities(be.getCode(), linkCode, linkValue);
	}

	public List<BaseEntity> getLinkedBaseEntities(String beCode, String linkCode, String linkValue) {

		List<BaseEntity> linkedBaseEntities = new ArrayList<BaseEntity>();
		try {

			/* We grab all the links from the node passed as a parameter "beCode" */
			List<EntityEntity> links = this.getLinks(beCode);

			/* We loop through all the links */
			for (EntityEntity link : links) {

				if (link != null && link.getLink() != null) {

					Link entityLink = link.getLink();

					/* We get the targetCode */
					String targetCode = entityLink.getTargetCode();
					if (targetCode != null) {

						/* We use the targetCode to get the base entity */
						BaseEntity targetBe = this.getBaseEntityByCode(targetCode);
						if (targetBe != null) {

							/* If a linkCode is passed we filter using its value */
							if (linkCode != null) {
								if (entityLink.getAttributeCode() != null
										&& entityLink.getAttributeCode().equals(linkCode)) {

									/* If a linkValue is passed we filter using its value */
									if (linkValue != null) {
										if (entityLink.getLinkValue() != null
												&& entityLink.getLinkValue().equals(linkValue)) {
											linkedBaseEntities.add(targetBe);
										}
									} else {

										/* If no link value was provided we just pass the base entity */
										linkedBaseEntities.add(targetBe);
									}
								}
							} else {

								/* If not linkCode was provided we just pass the base entity */
								linkedBaseEntities.add(targetBe);
							}
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		return linkedBaseEntities;
	}

	public List<BaseEntity> getBaseEntityWithChildren(String beCode, Integer level) {

		if (level == 0) {
			return null; // exit point;
		}

		level--;
		BaseEntity be = this.getBaseEntityByCode(beCode);
		if (be != null) {

			List<BaseEntity> beList = new ArrayList<BaseEntity>();

			Set<EntityEntity> entityEntities = be.getLinks();

			// we interate through the links
			for (EntityEntity entityEntity : entityEntities) {

				Link link = entityEntity.getLink();
				if (link != null) {

					// we get the target BE
					String targetCode = link.getTargetCode();
					if (targetCode != null) {

						// recursion
						beList.addAll(this.getBaseEntityWithChildren(targetCode, level));
					}
				}
			}

			return beList;
		}

		return null;
	}

	public Boolean checkIfLinkExists(String parentCode, String linkCode, String childCode) {

		Boolean isLinkExists = false;
		QDataBaseEntityMessage dataBEMessage = QwandaUtils.getDataBEMessage(parentCode, linkCode, this.token);

		if (dataBEMessage != null) {
			BaseEntity[] beArr = dataBEMessage.getItems();

			if (beArr.length > 0) {
				for (BaseEntity be : beArr) {
					if (be.getCode().equals(childCode)) {
						isLinkExists = true;
						return isLinkExists;
					}
				}
			} else {
				isLinkExists = false;
				return isLinkExists;
			}

		}
		return isLinkExists;
	}

	/* Check If Link Exists and Available */
	public Boolean checkIfLinkExistsAndAvailable(String parentCode, String linkCode, String linkValue,
			String childCode) {
		Boolean isLinkExists = false;
		List<Link> links = getLinks(parentCode, linkCode);
		if (links != null) {
			for (Link link : links) {
				String linkVal = link.getLinkValue();

				if (linkVal != null && linkVal.equals(linkValue)) {
					Double linkWeight = link.getWeight();
					if (linkWeight == 1.0) {
						isLinkExists = true;
						return isLinkExists;
					}
				}
			}
		}
		return isLinkExists;
	}

	/* returns a duplicated BaseEntity from an existing beCode */
	public BaseEntity duplicateBaseEntityAttributesAndLinks(final BaseEntity oldBe, final String bePrefix,
			final String name) {

    BaseEntity newBe = this.create(oldBe.getCode(), bePrefix, name);
		duplicateAttributes(oldBe, newBe);
		duplicateLinks(oldBe, newBe);
		return getBaseEntityByCode(newBe.getCode());
	}

	public BaseEntity duplicateBaseEntityAttributes(final BaseEntity oldBe, final String bePrefix, final String name) {

    BaseEntity newBe = this.create(oldBe.getCode(), bePrefix, name);
		duplicateAttributes(oldBe, newBe);
		return getBaseEntityByCode(newBe.getCode());
	}

	public BaseEntity duplicateBaseEntityLinks(final BaseEntity oldBe, final String bePrefix, final String name) {

  	BaseEntity newBe = this.create(oldBe.getCode(), bePrefix, name);
		duplicateLinks(oldBe, newBe);
		return getBaseEntityByCode(newBe.getCode());
	}

	public void duplicateAttributes(final BaseEntity oldBe, final BaseEntity newBe) {

  	List<Answer> duplicateAnswerList = new ArrayList<>();

		for (EntityAttribute ea : oldBe.getBaseEntityAttributes()) {
			duplicateAnswerList.add(new Answer(newBe.getCode(), newBe.getCode(), ea.getAttributeCode(), ea.getValue()));
		}

		this.saveAnswers(duplicateAnswerList);
	}

	public void duplicateLinks(final BaseEntity oldBe, final BaseEntity newBe) {
		for (EntityEntity ee : oldBe.getLinks()) {
			createLink(newBe.getCode(), ee.getLink().getTargetCode(), ee.getLink().getAttributeCode(),
					ee.getLink().getLinkValue(), ee.getLink().getWeight());
		}
	}

	public void duplicateLink(final BaseEntity oldBe, final BaseEntity newBe, final BaseEntity childBe) {
		for (EntityEntity ee : oldBe.getLinks()) {
			if (ee.getLink().getTargetCode() == childBe.getCode()) {

				createLink(newBe.getCode(), ee.getLink().getTargetCode(), ee.getLink().getAttributeCode(),
						ee.getLink().getLinkValue(), ee.getLink().getWeight());
				break;
			}
		}
	}

	public void duplicateLinksExceptOne(final BaseEntity oldBe, final BaseEntity newBe, String linkValue) {
		for (EntityEntity ee : oldBe.getLinks()) {
			if (ee.getLink().getLinkValue() == linkValue) {
				continue;
			}
			createLink(newBe.getCode(), ee.getLink().getTargetCode(), ee.getLink().getAttributeCode(),
					ee.getLink().getLinkValue(), ee.getLink().getWeight());
		}
	}

	public BaseEntity cloneBeg(final BaseEntity oldBe, final BaseEntity newBe, final BaseEntity childBe,
			String linkValue) {
		duplicateLinksExceptOne(oldBe, newBe, linkValue);
		duplicateLink(oldBe, newBe, childBe);
		return getBaseEntityByCode(newBe.getCode());
	}

	/* clones links of oldBe to newBe from supplied arraylist linkValues */
	public BaseEntity copyLinks(final BaseEntity oldBe, final BaseEntity newBe, final String[] linkValues) {
		System.out.println("linkvalues   ::   " + Arrays.toString(linkValues));
		for (EntityEntity ee : oldBe.getLinks()) {
			System.out.println("old be linkValue   ::   " + ee.getLink().getLinkValue());
			for (String linkValue : linkValues) {
				System.out.println("a linkvalue   ::   " + linkValue);
				if (ee.getLink().getLinkValue().equals(linkValue)) {
					createLink(newBe.getCode(), ee.getLink().getTargetCode(), ee.getLink().getAttributeCode(),
							ee.getLink().getLinkValue(), ee.getLink().getWeight());
					System.out.println("creating link for   ::   " + linkValue);
				}
			}
		}
		return getBaseEntityByCode(newBe.getCode());
	}

	/*
	 * clones all links of oldBe to newBe except the linkValues supplied in
	 * arraylist linkValues
	 */
	public BaseEntity copyLinksExcept(final BaseEntity oldBe, final BaseEntity newBe, final String[] linkValues) {
		System.out.println("linkvalues   ::   " + Arrays.toString(linkValues));
		for (EntityEntity ee : oldBe.getLinks()) {
			System.out.println("old be linkValue   ::   " + ee.getLink().getLinkValue());
			for (String linkValue : linkValues) {
				System.out.println("a linkvalue   ::   " + linkValue);
				if (ee.getLink().getLinkValue().equals(linkValue)) {
					continue;
				}
				createLink(newBe.getCode(), ee.getLink().getTargetCode(), ee.getLink().getAttributeCode(),
						ee.getLink().getLinkValue(), ee.getLink().getWeight());
				System.out.println("creating link for   ::   " + linkValue);
			}
		}
		return getBaseEntityByCode(newBe.getCode());
	}

	public void updateBaseEntityStatus(BaseEntity be, String userCode, String status) {
		this.updateBaseEntityStatus(be.getCode(), userCode, status);
	}

	public void updateBaseEntityStatus(String beCode, String userCode, String status) {

		String attributeCode = "STA_" + userCode;
		this.updateBaseEntityAttribute(userCode, beCode, attributeCode, status);
	}

	public void updateBaseEntityStatus(BaseEntity be, List<String> userCodes, String status) {
		this.updateBaseEntityStatus(be.getCode(), userCodes, status);
	}

	public void updateBaseEntityStatus(String beCode, List<String> userCodes, String status) {

		for (String userCode : userCodes) {
			this.updateBaseEntityStatus(beCode, userCode, status);
		}
	}

	public List<Link> getLinks(final String parentCode, final String linkCode) {
		List<Link> links = RulesUtils.getLinks(this.qwandaServiceUrl, this.decodedMapToken, this.token, parentCode,
				linkCode);
		return links;
	}


	public String updateBaseEntity(BaseEntity be) {
		try {
			VertxUtils.writeCachedJson(be.getCode(), JsonUtils.toJson(be));
			return QwandaUtils.apiPutEntity(this.qwandaServiceUrl + "/qwanda/baseentitys", JsonUtils.toJson(be),
					this.token);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public BaseEntity updateCachedBaseEntity(final Answer answer) {
		BaseEntity cachedBe = this.getBaseEntityByCode(answer.getTargetCode());
		// Add an attribute if not already there
		try {
			answer.setAttribute(RulesUtils.attributeMap.get(answer.getAttributeCode()));
			if (answer.getAttribute() == null) {
				System.out.println("Null Attribute");
			} else
				cachedBe.addAnswer(answer);
			VertxUtils.writeCachedJson(answer.getTargetCode(), JsonUtils.toJson(cachedBe));
		} catch (BadDataException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return cachedBe;
	}

	public BaseEntity updateCachedBaseEntity(final List<Answer> answers) {
		Answer firstanswer = null;
		if (answers != null) {
			if (!answers.isEmpty()) {
				firstanswer = answers.get(0);
			}
		}
		BaseEntity cachedBe = null;

		if (firstanswer != null) {
			cachedBe = this.getBaseEntityByCode(firstanswer.getTargetCode());
		} else {
			return null;
		}

		for (Answer answer : answers) {

			// Add an attribute if not already there
			try {
				answer.setAttribute(RulesUtils.attributeMap.get(answer.getAttributeCode()));
				if (answer.getAttribute() == null) {
					// log.error("Null Attribute");
				} else
					cachedBe.addAnswer(answer);

			} catch (BadDataException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		VertxUtils.writeCachedJson(cachedBe.getCode(), JsonUtils.toJson(cachedBe));
		return cachedBe;
	}

	public Link createLink(String groupCode, String targetCode, String linkCode, String linkValue, Double weight) {

		System.out.println("CREATING LINK between " + groupCode + "and" + targetCode + "with LINK VALUE = " + linkValue);
		Link link = new Link(groupCode, targetCode, linkCode, linkValue);
		link.setWeight(weight);
		try {
			QwandaUtils.apiPostEntity(qwandaServiceUrl + "/qwanda/entityentitys", JsonUtils.toJson(link), this.token);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return link;
	}

	public Link updateLink(String groupCode, String targetCode, String linkCode, String linkValue, Double weight) {

		System.out.println("UPDATING LINK between " + groupCode + "and" + targetCode + "with LINK VALUE = " + linkValue);
		Link link = new Link(groupCode, targetCode, linkCode, linkValue);
		link.setWeight(weight);
		try {
			QwandaUtils.apiPutEntity(qwandaServiceUrl + "/qwanda/links", JsonUtils.toJson(link), this.token);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return link;
	}

	public Link updateLink(String groupCode, String targetCode, String linkCode, Double weight) {

		System.out.println("UPDATING LINK between " + groupCode + "and" + targetCode);
		Link link = new Link(groupCode, targetCode, linkCode);
		link.setWeight(weight);
		try {
			QwandaUtils.apiPutEntity(qwandaServiceUrl + "/qwanda/links", JsonUtils.toJson(link), this.token);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return link;
	}

	public Link[] getUpdatedLink(String parentCode, String linkCode) {
		List<Link> links = this.getLinks(parentCode, linkCode);
		Link[] items = new Link[links.size()];
		items = (Link[]) links.toArray(items);
		return items;
	}

	/*
	 * Gets all the attribute and their value for the given basenentity code
	 */
	public Map<String, String> getMapOfAllAttributesValuesForBaseEntity(String beCode) {

		BaseEntity be = this.getBaseEntityByCode(beCode);
		System.out.println("The load is ::" + be);
		Set<EntityAttribute> eaSet = be.getBaseEntityAttributes();
		System.out.println("The set of attributes are  :: " + eaSet);
		Map<String, String> attributeValueMap = new HashMap<String, String>();
		for (EntityAttribute ea : eaSet) {
			String attributeCode = ea.getAttributeCode();
			System.out.println("The attribute code  is  :: " + attributeCode);
			String value = ea.getAsLoopString();
			attributeValueMap.put(attributeCode, value);
		}

		return attributeValueMap;
	}

	public List getLinkList(String groupCode, String linkCode, String linkValue, String token) {

		// String qwandaServiceUrl = "http://localhost:8280";
		String qwandaServiceUrl = System.getenv("REACT_APP_QWANDA_API_URL");
		List linkList = null;

		try {
			String attributeString = QwandaUtils.apiGet(qwandaServiceUrl + "/qwanda/entityentitys/" + groupCode
					+ "/linkcodes/" + linkCode + "/children/" + linkValue, token);
			if (attributeString != null) {
				linkList = JsonUtils.fromJson(attributeString, List.class);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return linkList;

	}

	/*
	 * Sorting Columns of a SearchEntity as per the weight in either Ascening or
	 * descending order
	 */
	public List<String> sortEntityAttributeBasedOnWeight(final List<EntityAttribute> ea, final String sortOrder) {

		if (ea.size() > 1) {
			Collections.sort(ea, new Comparator<EntityAttribute>() {

				@Override
				public int compare(EntityAttribute ea1, EntityAttribute ea2) {
					if (ea1.getWeight() != null && ea2.getWeight() != null) {
						if (sortOrder.equalsIgnoreCase("ASC"))
							return (ea1.getWeight()).compareTo(ea2.getWeight());
						else
							return (ea2.getWeight()).compareTo(ea1.getWeight());

					} else
						return 0;
				}
			});
		}

		List<String> searchHeader = new ArrayList<String>();
		for (EntityAttribute ea1 : ea) {
			searchHeader.add(ea1.getAttributeCode().substring("COL_".length()));
		}

		return searchHeader;
	}

	public BaseEntity baseEntityForLayout(String realm, String token, Layout layout) {

		if (layout.getPath() == null) {
			return null;
		}

		String serviceToken = RulesUtils.generateServiceToken(realm);
		if(serviceToken != null) {

			BaseEntity beLayout = null;

			/* we check if the baseentity for this layout already exists */
			// beLayout =
			// RulesUtils.getBaseEntityByAttributeAndValue(RulesUtils.qwandaServiceUrl,
			// this.decodedTokenMap, this.token, "PRI_LAYOUT_URI", layout.getPath());
			String precode = layout.getPath().replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
			String layoutCode = ("LAY_" + realm + "_" + precode).toUpperCase();

			beLayout = this.getBaseEntityByCode(layoutCode);

			/* if the base entity does not exist, we create it */
			if (beLayout == null) {

				/* otherwise we create it */
				beLayout = QwandaUtils.createBaseEntityByCode(layoutCode, layout.getName(), this.qwandaServiceUrl, serviceToken);
				VertxUtils.writeCachedJson(beLayout.getCode(), JsonUtils.toJson(beLayout));
			}

			if (beLayout != null) {

				this.addAttributes(beLayout);

				/*
				 * we get the modified time stored in the BE and we compare it to the layout one
				 */
				String beModifiedTime = beLayout.getValue("PRI_LAYOUT_MODIFIED_DATE", null);

				if (beModifiedTime == null || layout.getModifiedDate() == null || !beModifiedTime.equals(layout.getModifiedDate())) {

					System.out.println("Reloading layout: " + layoutCode);

					/* if the modified time is not the same, we update the layout BE */

					/* setting layout attributes */
					List<Answer> answers = new ArrayList<Answer>();

					/* download the content of the layout */
					String content = LayoutUtils.downloadLayoutContent(layout);

					Answer newAnswerContent = new Answer(beLayout.getCode(), beLayout.getCode(), "PRI_LAYOUT_DATA",
							content);
					newAnswerContent.setChangeEvent(true);
					answers.add(newAnswerContent);

					Answer newAnswer = new Answer(beLayout.getCode(), beLayout.getCode(), "PRI_LAYOUT_URI",
							layout.getPath());
					newAnswer.setChangeEvent(false);
					answers.add(newAnswer);

					Answer newAnswer2 = new Answer(beLayout.getCode(), beLayout.getCode(), "PRI_LAYOUT_URL",
							layout.getDownloadUrl());
					newAnswer2.setChangeEvent(false);
					answers.add(newAnswer2);

					Answer newAnswer3 = new Answer(beLayout.getCode(), beLayout.getCode(), "PRI_LAYOUT_NAME",
							layout.getName());
					newAnswer3.setChangeEvent(false);
					answers.add(newAnswer3);

					Answer newAnswer4 = new Answer(beLayout.getCode(), beLayout.getCode(), "PRI_LAYOUT_MODIFIED_DATE",
							layout.getModifiedDate());
					newAnswer4.setChangeEvent(false);
					answers.add(newAnswer4);

					this.saveAnswers(answers);

					/* create link between GRP_LAYOUTS and this new LAY_XX base entity */
					this.createLink("GRP_LAYOUTS", beLayout.getCode(), "LNK_CORE", "LAYOUT", 1.0);
				}
			}

			return this.getBaseEntityByCode(beLayout.getCode());
		}

		return null;
	}
}
