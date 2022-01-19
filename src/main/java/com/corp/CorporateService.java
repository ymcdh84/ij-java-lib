package com.corp;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class CorporateService {

	public String getCorporateStatus(String corNum) {

		String trtCntn = "";

		//Request Body 값
		String paramData = "<map id=\"ATTABZAA001R08\">" +
				" 				<pubcUserNo/>" +
				" 				<mobYn>N</mobYn>" +
				" 				<inqrTrgtClCd>1</inqrTrgtClCd>" +
				" 				<txprDscmNo>" + corNum.replace("-","") + "</txprDscmNo>" +
				" 				<dongCode>" + corNum.replace("-","").substring(3,5) + "</dongCode>" +
				" 				<psbSearch>Y</psbSearch>" +
				" 				<map id=\"userReqInfoVO\"/>" +
				" 			</map>";

		try {

			String urlStr = "https://teht.hometax.go.kr/wqAction.do?actionId=ATTABZAA001R08&screenId=UTEABAAA13&popupYn=false&realScreenId=";

			URL url = new URL(urlStr);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			if(conn != null){
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Content-type", "application/xml");
				conn.setRequestProperty("Content-Length",String.valueOf(paramData.getBytes().length));
				conn.setDoOutput(true);

				//요청 데이터 보내기
				DataOutputStream dataOutputstr = new DataOutputStream(conn.getOutputStream());
				dataOutputstr.writeBytes(paramData);
				dataOutputstr.flush(); //데이터 스트림의 내용을 출력.

				// 응답 내용(BODY)의 사업자 상태 추출
				InputStream instr = conn.getInputStream();
				BufferedReader br = new BufferedReader(new InputStreamReader(instr, "UTF-8"));

				String temp;

				while((temp = br.readLine()) != null){
					System.out.println(temp);

					if(temp.contains("<trtCntn>")){
						trtCntn = temp.split("<trtCntn>")[1].split("</trtCntn>")[0];
					}
				}
				
				// 접속 해제
				dataOutputstr.close();
				conn.disconnect();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return trtCntn;
	}
}
