package life.genny.utils;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.client.ClientProtocolException;
import org.apache.logging.log4j.Logger;

import life.genny.qwanda.Ask;
import life.genny.qwanda.attribute.Attribute;
import life.genny.qwanda.datatype.DataType;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.message.QBulkMessage;
import life.genny.qwanda.message.QDataAskMessage;
import life.genny.qwanda.message.QDataBaseEntityMessage;
import life.genny.qwanda.validation.Validation;
import life.genny.qwandautils.GennySettings;
import life.genny.qwandautils.JsonUtils;
import life.genny.qwandautils.QwandaMessage;
import life.genny.qwandautils.QwandaUtils;

public class QuestionUtils {
	
	protected static final Logger log = org.apache.logging.log4j.LogManager
			.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

	
	private QuestionUtils() {}
	
	public static Boolean doesQuestionGroupExist(String sourceCode, String targetCode, final String questionCode, String token) {

		/* we grab the question group using the questionCode */
		QDataAskMessage questions = getAsks(sourceCode, targetCode, questionCode, token);

		/* we check if the question payload is not empty */
		if (questions != null) {

			/* we check if the question group contains at least one question */
			if (questions.getItems() != null && questions.getItems().length > 0) {

				Ask firstQuestion = questions.getItems()[0];

				/* we check if the question is a question group */
				if (firstQuestion.getAttributeCode().contains("QQQ_QUESTION_GROUP_BUTTON_SUBMIT")) {

					/* we see if this group contains at least one question */
					return firstQuestion.getChildAsks().length > 0;
				} 
				else {

					/* if it is an ask we return true */
					return true;
				}
			}
		}

		/* we return false otherwise */
		return false;
	}

	public static QDataAskMessage getAsks(String sourceCode, String targetCode, String questionCode, String token) {

		String json;
		try {

			json = QwandaUtils.apiGet(GennySettings.qwandaServiceUrl + "/qwanda/baseentitys/" + sourceCode + "/asks2/"
					+ questionCode + "/" + targetCode, token);
			return JsonUtils.fromJson(json, QDataAskMessage.class);
		} 
		catch (ClientProtocolException e) {
			System.out.println(e.getMessage());
		} 
		catch (IOException e) {
			System.out.println(e.getMessage());
		}

		return null;
	}

	public static QwandaMessage getQuestions(String sourceCode, String targetCode, String questionCode, String token) throws ClientProtocolException, IOException {
		return getQuestions(sourceCode, targetCode, questionCode, token, null, true);
	}

	public static QwandaMessage getQuestions(String sourceCode, String targetCode, String questionCode, String token, String stakeholderCode, Boolean pushSelection) throws ClientProtocolException, IOException {

		QBulkMessage bulk = new QBulkMessage();
		QwandaMessage qwandaMessage = new QwandaMessage();

		long startTime2 = System.nanoTime();

		QDataAskMessage questions = getAsks(sourceCode, targetCode, questionCode, token);
		long endTime2 = System.nanoTime();
		double difference2 = (endTime2 - startTime2) / 1e6; // get ms
		RulesUtils.println("getAsks fetch Time = "+difference2+" ms");

		if (questions != null) {

			/*
			 * if we have the questions, we loop through the asks and send the required data
			 * to front end
			 */
			long startTime = System.nanoTime();
			Ask[] asks = questions.getItems();
			if (asks != null && pushSelection) {
				QBulkMessage askData = sendAsksRequiredData(asks, token, stakeholderCode);
				for(QDataBaseEntityMessage message: askData.getMessages()) {
					bulk.add(message);
				}
			}
			long endTime = System.nanoTime();
			double difference = (endTime - startTime) / 1e6; // get ms
			RulesUtils.println("sendAsksRequiredData fetch Time = "+difference+" ms");

			qwandaMessage.askData = bulk;
			qwandaMessage.asks = questions;

			return qwandaMessage;

		} 
		else {
			log.error("Questions Msg is null " + sourceCode + "/asks2/" + questionCode + "/" + targetCode);
		}

		return null;
	}

	public static QwandaMessage askQuestions(final String sourceCode, final String targetCode, final String questionGroupCode, String token) {
		return askQuestions(sourceCode, targetCode, questionGroupCode, token, null, true);
	}
	
	public static QwandaMessage askQuestions(final String sourceCode, final String targetCode, final String questionGroupCode, String token, String stakeholderCode) {
		return askQuestions(sourceCode, targetCode, questionGroupCode, token, stakeholderCode, true);
	}
	
	public static QwandaMessage askQuestions(final String sourceCode, final String targetCode, final String questionGroupCode, Boolean pushSelection) {
		return askQuestions(sourceCode, targetCode, questionGroupCode, null, null, pushSelection);
	}
	
	public static QwandaMessage askQuestions(final String sourceCode, final String targetCode, final String questionGroupCode, String token, Boolean pushSelection) {
		return askQuestions(sourceCode, targetCode, questionGroupCode, token, null, pushSelection);
	}

	public static QwandaMessage askQuestions(final String sourceCode, final String targetCode, final String questionGroupCode, final String token, final String stakeholderCode, final Boolean pushSelection) {

		try {

			/* if sending the questions worked, we ask user */
			return getQuestions(sourceCode, targetCode, questionGroupCode, token, stakeholderCode, pushSelection);

		}
		catch (Exception e) {
			System.out.println("Ask questions exception: ");
			e.printStackTrace();
			return null;
		}
	}
	
	public static QwandaMessage setCustomQuestion(QwandaMessage questions, String questionAttributeCode, String customTemporaryQuestion) {
		
		if(questions != null && questionAttributeCode != null) {
			Ask[] askArr = questions.asks.getItems();
			if(askArr != null && askArr.length > 0) {
				for(Ask ask : askArr) {
					Ask[] childAskArr = ask.getChildAsks();
					if(childAskArr != null && childAskArr.length > 0) {
						for(Ask childAsk : childAskArr) {
							System.out.println("child ask code :: "+childAsk.getAttributeCode() + ", child ask name :: "+childAsk.getName());
							if(childAsk.getAttributeCode().equals(questionAttributeCode)) {
								if(customTemporaryQuestion != null) {
									childAsk.getQuestion().setName(customTemporaryQuestion);
									return questions;
								}								
							}
						}
					}
				}
			}	
		}
		return questions;
	}
	
	private static QBulkMessage sendAsksRequiredData(Ask[] asks, String token, String stakeholderCode) {

		QBulkMessage bulk = new QBulkMessage();

		/* we loop through the asks and send the required data if necessary */
		for (Ask ask : asks) {

			/*
			 * we get the attribute code. if it starts with "LNK_" it means it is a dropdown
			 * selection.
			 */

			String attributeCode = ask.getAttributeCode();
			if (attributeCode != null && attributeCode.startsWith("LNK_")) {

				/* we get the attribute validation to get the group code */
				Attribute attribute = RulesUtils.getAttribute(attributeCode, token);
				if(attribute != null) {

					/* grab the group in the validation */
					DataType attributeDataType = attribute.getDataType();
					if(attributeDataType != null) {

						List<Validation> validations = attributeDataType.getValidationList();

						/* we loop through the validations */
						for(Validation validation: validations) {

							List<String> validationStrings = validation.getSelectionBaseEntityGroupList();

							for(String validationString: validationStrings) {

								if(validationString.startsWith("GRP_")) {

									/* we have a GRP. we push it to FE */
									List<BaseEntity> bes = CacheUtils.getChildren(validationString, 2, token);
									List<BaseEntity> filteredBes = null;

									if(bes != null && bes.size() > 0) {
										
										/* hard coding this for now. sorry */
										if(attributeCode.equals("LNK_LOAD_LISTS") && stakeholderCode != null) {

											/* we filter load you only are a stakeholder of */
											filteredBes = bes.stream().filter(baseEntity -> {
												return baseEntity.getValue("PRI_AUTHOR", "").equals(stakeholderCode);
											}).collect(Collectors.toList());
										}
										else {
											filteredBes = bes;
										}

										QDataBaseEntityMessage beMessage = new QDataBaseEntityMessage(filteredBes);
										beMessage.setLinkCode("LNK_CORE");
										beMessage.setParentCode(validationString);
										beMessage.setReplace(true);
										bulk.add(beMessage);
									}
								}
							}
						}
					}
				}
			}

			/* recursive call */
			Ask[] childAsks = ask.getChildAsks();
			if (childAsks != null && childAsks.length > 0) {

				QBulkMessage newBulk = sendAsksRequiredData(childAsks, token, stakeholderCode);
				for(QDataBaseEntityMessage msg: newBulk.getMessages()) {
					bulk.add(msg);
				}
			}
		}

		return bulk;
	}
}