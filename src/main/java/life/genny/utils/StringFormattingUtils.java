package life.genny.utils;

public class StringFormattingUtils {
	
	/*
	 * @param str: String - string to mask
	 * @param start: int - length of the string
	 * @param end: int - number of characters to be NOT masked 
	 * @param maskCharacter: String - mask character
	 * @return String - masked str
	 */
	public static String mask(String str, int lengthOfWord, int numberOfIntegersTobeFormatted, String maskCharacter) {
		
		/* we check if we are not out of range or if the passed str is null */
		if(str == null || lengthOfWord == 0)  {
			return null;
		}
		
			 
		if(lengthOfWord - numberOfIntegersTobeFormatted < 0) {
			return null;
		}
			
		
		int maskLength = lengthOfWord - numberOfIntegersTobeFormatted; 
		if(maskLength > str.length()) {
			return null;
		}
		
		
		/* we create a mask with the right length 
		 * We skip masking if the string has '-' character since its the card seperator
		 * */
		StringBuilder maskedStr = new StringBuilder();
		for(int i = 0; i < maskLength; i++) {
			
			char c = str.charAt(i);
			if(c == '-') {
				maskedStr.append(c);
			} else {
				maskedStr.append(maskCharacter);
			}
			
		}
		
		/* we return: originalString until start of mask + originalString from end of mask */
		/*
		 * example:
		 * str = 1234-1234
		 * length = 9
		 * numberOfIntegersToBeFormatted = 4
		 * return: xxxx-1234
		 */
		String restoredCardDetail = str.substring(maskLength, str.length());
		System.out.println("card detail ::"+(maskedStr + restoredCardDetail));
		
		return maskedStr + restoredCardDetail;
	}


}