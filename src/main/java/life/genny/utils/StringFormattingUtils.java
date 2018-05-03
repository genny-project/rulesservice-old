package life.genny.utils;

public class StringFormattingUtils {

	
	/**
	 * 
	 * @param str - String to be masked
	 * @param start - start range for masking
	 * @param end - end range for masking
	 * @param maskCharacter - character to be used for masking
	 * @param ignoreCharacterArrayForMask - If there are any character to be ignored while masking, they will put in the ignoreCharacterArrayForMask
	 * @return masked String
	 */
	/* Currently used for bank credential number masking */
	public static String maskWithRange(String str, int start, int end, String maskCharacter, Character[] ignoreCharacterArrayForMask) {
		
		/* we check if we are not out of range or if the passed str is null */
		if(str == null || str.length() == 0) return null; 
		if(end - start < 0) return null;
		
		int maskLength = end - start;
		if(maskLength > str.length()) return null;
		
		StringBuilder newStr = new StringBuilder();
		
		/* we create a mask with the right length */
		for(int i = 0; i < maskLength; i++) {
			
			char c = str.charAt(i);
			
			/* If there are any character to be ignored while masking, they will put in the ignoreCharacterArrayForMask */
			if(ignoreCharacterArrayForMask != null && ignoreCharacterArrayForMask.length > 0) {
				
				/* iterating through each ignoreMaskCharacter */
				for(Character ignoreCharacterForMask : ignoreCharacterArrayForMask) {
					if(c == ignoreCharacterForMask) {
						/* If a character of word matched character to be ignored, then the character will not be masked */
						newStr.append(c);
					} else {
						newStr.append(maskCharacter);
					}
				}
				
			} else {
				newStr.append(maskCharacter);
			}
			
			
		}
		
		/* we return: originalString until start of mask + mask + originalString from end of mask */
		/*
		 * example:
		 * str = 1234-1234
		 * start = 0
		 * end = 4
		 * return: xxxx-1234
		 */
		
		return str.substring(0, start) + newStr + str.substring(end, str.length());
	}


}