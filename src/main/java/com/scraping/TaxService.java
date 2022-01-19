package com.scraping;

import org.apache.commons.lang3.time.DateUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import tradesign.crypto.provider.JeTS;
import tradesign.crypto.provider.rsa.RSAPrivateKey;
import tradesign.pki.pkcs.EncPrivateKeyInfo;
import tradesign.pki.pkix.X509Certificate;
import tradesign.pki.util.FileUtil;
import tradesign.pki.util.JetsUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.Signature;
import java.text.SimpleDateFormat;
import java.util.*;

public class TaxService{

    String jetsRoot;
    String xmlFileDir;

    public TaxService(String jetsRoot, String xmlFileDir) {
        this.jetsRoot = jetsRoot;
        this.xmlFileDir = xmlFileDir;
    }

    public Map<String, Object> certificateKey(String root) throws Exception {

        try{
            JeTS.installProvider(jetsRoot);
        } catch (Exception ex){
            //logger.error("오류 발생: 예기치 않은 오류 " + ex);
        }

        Map<String, Object> key = new HashMap<>();

        X509Certificate inputCert = new X509Certificate(new FileInputStream(root + "/signCert.der"));
        String pemCert = JetsUtil.toPem(inputCert);

        String pemPriv = JetsUtil.toPem(new EncPrivateKeyInfo(FileUtil.getBytesFromFile(root + "/signPri.key")));

        key.put("pemCert",pemCert);
        key.put("pemPriv",pemPriv);

        return key;
    }
    /**
     * 홈택스 wmonId, txppSessionId, pkcEncSsn 값을 얻기 위한 API
     * @return
     * @throws Exception
     */
    public Map<String, Object> urlHometaxWqAction() throws Exception {
        Map<String, Object> rst = new HashMap<>();

        try {
            String wmonId = "";
            String txppSessionId = "";
            String pkcEncSsn = "";

            String urlStr = "https://www.hometax.go.kr/wqAction.do?actionId=ATXPPZXA001R01&screenId=UTXPPABA01";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if(conn != null){

                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-type", "application/xml");

                int resCode = conn.getResponseCode();

                if(resCode == HttpURLConnection.HTTP_OK){
                    
                    // 응답 헤더(Header) 쿠키의 WMOID, TXPPsessionID 정보 획득
                    Map<String, List<String>> header = conn.getHeaderFields();
                    if(header.containsKey("Set-Cookie")){

                        List<String> cookie = header.get("Set-Cookie");

                        for(int i = 0; i < cookie.size(); i++){

                            String[] element = cookie.get(i).split(";");

                            for(int j = 0; j < element.length ; j++){
                                if("WMONID".equals(element[j].split("=")[0])){
                                    wmonId = element[j].split("=")[1];
                                }

                                if("TXPPsessionID".equals(element[j].split("=")[0])){
                                    txppSessionId = element[j].split("=")[1];
                                }
                            }
                        }
                    }

                    // 응답 내용(BODY)의 서명 문자열 구하기
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String temp;

                    while((temp = br.readLine()) != null){
                        if(temp.contains("<pkcEncSsn>")){
                            pkcEncSsn=  temp.split("<pkcEncSsn>")[1].split("</pkcEncSsn>")[0];
                        }
                    }
                }

                rst.put("wmonId",wmonId);
                rst.put("txppSessionId",txppSessionId);
                rst.put("pkcEncSsn",pkcEncSsn);

                // 접속 해제
                conn.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return rst;
    }

    /**
     * 홈택스 로그인 API 호출
     * Request Cookies값
     * 1. WMONID : pkcEncSsn
     * 2. NTS_LOGIN_SYSTEM_CODE_P : 'TXPP'
     * 3. TXPPsessionID : txppSessionId
     *
     * POST 방식 body 값
     * 1. cert : 서명용 공개키 PEM 값
     * 2. logSgnt : pkcEncSsn + '$'
     *             + 서명용 공개키 인증서 일련번호 + '$'
     *             + yyyyMMddHHmmss형식 오늘날짜 문자열 + '$'
     *             + pkcEncSsn 서명 결과값 => Base65 인코딩값
     * 3. pkcLgnClCd : '04'
     * 4. pkcLoginYnImpv : 'Y'
     * 5. randomEnc : 서명용 공개키 RANDOM값
     */
    public Map<String, Object> urlHometaxPubcLogin(Map pWqInfo) throws Exception {
        Map<String, Object> rst = new HashMap<>();

        //Request Cookie 값
        String wmonId = pWqInfo.get("wmonId").toString();
        String txppSessionId = pWqInfo.get("txppSessionId").toString();

        String setCookie =  "WMONID=" + wmonId + "; "
                + "nts_homtax:zoomVal=100; nts_hometax:pkckeyboard=none; "
                + "NTS_LOGIN_SYSTEM_CODE_P=TXPP; "
                + "TXPPsessionID=" + txppSessionId;

        //Request Body 값
        String cert = pWqInfo.get("cert").toString();
        String logSgnt = pWqInfo.get("logSgnt").toString();
        String pkcLgnClCd = pWqInfo.get("pkcLgnClCd").toString();
        String pkcLoginYnImpv = pWqInfo.get("pkcLoginYnImpv").toString();
        String randomEnc = pWqInfo.get("randomEnc").toString();

        String paramData = URLEncoder.encode("cert", "UTF-8") + "=" + URLEncoder.encode(cert, "UTF-8");
        paramData += "&" + URLEncoder.encode("logSgnt", "UTF-8") + "=" + URLEncoder.encode(logSgnt, "UTF-8");
        paramData += "&" + URLEncoder.encode("pkcLgnClCd", "UTF-8") + "=" + URLEncoder.encode(pkcLgnClCd, "UTF-8");
        paramData += "&" + URLEncoder.encode("pkcLoginYnImpv", "UTF-8") + "=" + URLEncoder.encode(pkcLoginYnImpv, "UTF-8");
        paramData += "&" + URLEncoder.encode("randomEnc", "UTF-8") + "=" + URLEncoder.encode(randomEnc, "UTF-8");

        try {
            String urlStr = "https://www.hometax.go.kr/pubcLogin.do?domain=hometax.go.kr&mainSys=Y";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if(conn != null){
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Cookie", setCookie);
                conn.setRequestProperty("Content-Length",String.valueOf(paramData.getBytes().length));
                conn.setDoInput(true);
                conn.setDoOutput(true);

                //요청 데이터 보내기
                DataOutputStream dataOutputstr = new DataOutputStream(conn.getOutputStream());
                dataOutputstr.writeBytes(paramData);
                dataOutputstr.flush(); //데이터 스트림의 내용을 출력.

                // 응답 헤더(Header) 쿠키의 WMOID, TXPPsessionID 정보 획득
                Map<String, List<String>> header = conn.getHeaderFields();

                String rtTxppSessionId = "";

                if(header.containsKey("Set-Cookie")){

                    List<String> cookie = header.get("Set-Cookie");

                    for(int i = 0; i < cookie.size(); i++){

                        String[] element = cookie.get(i).split(";");

                        for(int j = 0; j < element.length ; j++){
                            if("TXPPsessionID".equals(element[j].split("=")[0])){
                                rtTxppSessionId = element[j].split("=")[1];
                            }
                        }
                    }
                }

                // 응답 내용(BODY)의 서명 문자열 구하기
                InputStream instr = conn.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(instr));

                String temp;
                String errMsg = "";

                while((temp = br.readLine()) != null){
                    if(temp.contains("decodeURIComponent('")){
                        errMsg = temp.split("decodeURIComponent\\('")[1].split("'\\).replace\\(")[0];
                    }
                    if(errMsg.length() > 0){
                        System.out.println(URLDecoder.decode(errMsg, "utf-8"));
                    }
                }

                dataOutputstr.close();

                rst.put("wmonId",wmonId);
                rst.put("txppSessionId",rtTxppSessionId);


                // 접속 해제
                conn.disconnect();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
            //e.printStackTrace();
        }

        return rst;
    }

    /**
     * 캐인키 랜덤값을 얻기 위한 함수
     * @return
     * @throws Exception
     */
    public String randomPrivateKey(String certB64, String privB64, String idn, String pw) {

        String s_random = "";

        try {
            byte[] pwd = pw.getBytes();

            try{
                JeTS.installProvider(jetsRoot);
            } catch (Exception ex){
                //logger.error("오류 발생: 예기치 않은 오류 " + ex);
            }

            byte[] certBytes = JetsUtil.base64ToBytes(certB64);
            byte[] privBytes = JetsUtil.base64ToBytes(privB64);

            EncPrivateKeyInfo enc = new EncPrivateKeyInfo(privBytes);
            RSAPrivateKey priv = (RSAPrivateKey) enc.decrypt(pwd);

            byte[] random = priv.getRandom();
            s_random =  Base64.getEncoder().encodeToString(random);

            X509Certificate x509 = new X509Certificate(certBytes);

            if (x509.VerifyIDN(idn.getBytes(), random)) System.out.println("본인확인 성공!");
            else System.out.println("본인확인 실패!");

            for (byte b : pwd) { b = 0; }

            //Thread.sleep(500000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return s_random;
    }

    /**
     * 서명값 호출 함수
     * @return
     * @throws Exception
     */
    public String signedResult(String root, String singMsg, String pw) {

        String s_result = "";

        try {
            String certPath = root + "/signCert.der";
            String privPath = root + "/signPri.key";

            // 프로바이더 설정
            try{
                JeTS.installProvider(jetsRoot);
            } catch (Exception ex){
                //logger.error("오류 발생: 예기치 않은 오류 " + ex);
            }

            //Cert
            //X509Certificate cert = new X509Certificate(FileUtil.getBytesFromFile(certPath));

            //Priv
            EncPrivateKeyInfo enc = new EncPrivateKeyInfo(FileUtil.getBytesFromFile(privPath));
            byte[] privPwdBytes = pw.getBytes();
            RSAPrivateKey privKey = (RSAPrivateKey) enc.decrypt(privPwdBytes);

            Signature sig = Signature.getInstance("SHA256WithRSA", "JeTS");

            byte[] msg = singMsg.getBytes();
            sig.initSign(privKey);
            sig.update(msg);

            byte[] sigText = sig.sign();
            String sigB64 = JetsUtil.toBase64String(sigText);
//            System.out.println(sigB64);
//
//            sig.initVerify(cert.getPublicKey());
//            sig.update(msg);
//            sig.verify(sigText);

            s_result = sigB64;

        }
        catch(Exception e)
        {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        return s_result;
    }

    /**
     * 로그인 사용자 정보 호출
     * @param pWqInfo
     * @return
     * @throws Exception
     */
    public Map<String, Object> urlHometaxLoginInfo(Map pWqInfo) throws Exception {
        Map<String, Object> rst = new HashMap<>();

        //Request Cookie 값
        String wmonId = pWqInfo.get("wmonId").toString();
        String txppSessionId = pWqInfo.get("txppSessionId").toString();

        String setCookie =  "WMONID=" + wmonId + "; "
                + "nts_homtax:zoomVal=100; nts_hometax:pkckeyboard=none; "
                + "nts_hometax:userId=''; "
                + "NTS_LOGIN_SYSTEM_CODE_P=TXPP; "
                + "TXPPsessionID=" + txppSessionId + ";"
                + "NTS_REQUEST_SYSTEM_CODE_P=TXPP; ";

        //Request Body 값
        String paramData =  "<map id='postParam'><popupYn>false</popupYn></map>";

        try {
            String tin = "";
            String urlStr = "https://www.hometax.go.kr/permission.do?screenId=index_pp\n";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if(conn != null){
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-type", "application/xml");
                conn.setRequestProperty("Cookie", setCookie);
                conn.setRequestProperty("Content-Length",String.valueOf(paramData.getBytes().length));
                conn.setDoInput(true);
                conn.setDoOutput(true);

                //요청 데이터 보내기
                DataOutputStream dataOutputstr = new DataOutputStream(conn.getOutputStream());
                dataOutputstr.writeBytes(paramData);
                dataOutputstr.flush(); //데이터 스트림의 내용을 출력.

                // 응답 내용(BODY)의 tin 값 구하기
                InputStream instr = conn.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(instr));

                String temp;

                while((temp = br.readLine()) != null){
                    if(temp.contains("<tin>")){
                        tin = temp.split("<tin>")[1].split("</tin>")[0];
                    }
                }

                dataOutputstr.close();

                rst.put("tin",tin);

                // 접속 해제
                conn.disconnect();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
            //e.printStackTrace();
        }

        return rst;
    }

    /**
     * teetSeesionId 추출
     * @param pWqInfo
     * @return
     * @throws Exception
     */
    public Map<String, Object> urlHometaxTEETSessionID(Map pWqInfo) throws Exception {
        Map<String, Object> rst = new HashMap<>();

        //Request Cookie 값
        String wmonId = pWqInfo.get("wmonId").toString();
        String txppSessionId = pWqInfo.get("txppSessionId").toString();

        String setCookie =  "WMONID=" + wmonId + "; "
                + "nts_homtax:zoomVal=100; nts_hometax:pkckeyboard=none; "
                + "nts_hometax:userId=''; "
                + "NTS_LOGIN_SYSTEM_CODE_P=TXPP; "
                + "TXPPsessionID=" + txppSessionId + ";"
                + "NTS_REQUEST_SYSTEM_CODE_P=TXPP; ";

        //Request Body 값
        String paramData =  "<map id='postParam'><popupYn>false</popupYn></map>";

        try {
            String tin = "";
            String urlStr = "https://teet.hometax.go.kr/permission.do?screenId=UTEETBDA01\n";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if(conn != null){
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-type", "application/xml");
                conn.setRequestProperty("Cookie", setCookie);
                conn.setRequestProperty("Content-Length",String.valueOf(paramData.getBytes().length));
                conn.setDoInput(true);
                conn.setDoOutput(true);

                //요청 데이터 보내기
                DataOutputStream dataOutputstr = new DataOutputStream(conn.getOutputStream());
                dataOutputstr.writeBytes(paramData);
                dataOutputstr.flush(); //데이터 스트림의 내용을 출력.

                // 응답 헤더(Header) 쿠키의 TEETsessionID 정보 획득
                Map<String, List<String>> header = conn.getHeaderFields();

                String rtTeetSessionId = "";

                if(header.containsKey("Set-Cookie")){

                    List<String> cookie = header.get("Set-Cookie");

                    for(int i = 0; i < cookie.size(); i++){

                        String[] element = cookie.get(i).split(";");

                        for(int j = 0; j < element.length ; j++){
                            if("TEETsessionID".equals(element[j].split("=")[0])){
                                rtTeetSessionId = element[j].split("=")[1];
                            }
                        }
                    }
                }


                dataOutputstr.close();

                rst.put("teetSessionId",rtTeetSessionId);

                // 접속 해제
                conn.disconnect();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
            //e.printStackTrace();
        }

        return rst;
    }

    /**
     * SSO 토큰 호출
     * @param pWqInfo
     * @return
     * @throws Exception
     */
    public String urlHometaxSsoToken(Map pWqInfo) throws Exception {
        String rst = "";

        //Request Cookie 값
        String wmonId = pWqInfo.get("wmonId").toString();
        String txppSessionId = pWqInfo.get("txppSessionId").toString();
        String teetSessionId = pWqInfo.get("teetSessionId").toString();

        String setCookie =  "WMONID=" + wmonId + "; "
                + "nts_homtax:zoomVal=100; nts_hometax:pkckeyboard=none; "
                + "NTS_LOGIN_SYSTEM_CODE_P=TXPP; "
                + "TXPPsessionID=" + txppSessionId + ";"
                + "NTS_REQUEST_SYSTEM_CODE_P=TXPP; "
                + "TEETsessionID=" + teetSessionId + ";";

        try {

            Date today = new Date();
            Locale currentLocale = new Locale("KOREAN", "KOREA");
            String toDate = "yyyy_MM_dd"; //hhmmss로 시간,분,초만 뽑기도 가능
            SimpleDateFormat formatter = new SimpleDateFormat(toDate,currentLocale);

            // _ + 20자리로 된 랜덤 값
            String nts_generateRandomString = "_" + this.getNtsGenerateRandomString(20);
            String urlStr = "https://hometax.go.kr/token.do?query=" + nts_generateRandomString + "&postfix=" + formatter.format(today);

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if(conn != null){
                conn.setRequestMethod("GET");

                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setRequestProperty("Cookie", setCookie);

                conn.setDoInput(true);
                conn.getResponseCode();

                // 응답 내용(BODY)의 tin 값 구하기
                InputStream instr = conn.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(instr));

                String temp;
                String ssoToken = "";
                while((temp = br.readLine()) != null){
                    if(temp.contains("<ssoToken>")){
                        ssoToken = temp.split("<ssoToken>")[1].split("</ssoToken>")[0];
                    }
                }
                //dataOutputstr.close();
                // 접속 해제
                conn.disconnect();

                rst = ssoToken;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
            //e.printStackTrace();
        }

        return rst;
    }

    /**
     * SSO 로그인 (전자세금계산서 시스템 로그인)
     * @param pWqInfo
     * @return
     * @throws Exception
     */
    public String urlHometaxSsoLogin(Map pWqInfo) throws Exception {
        String rst = "";

        //Request Cookie 값
        String wmonId = pWqInfo.get("wmonId").toString();
        String txppSessionId = pWqInfo.get("txppSessionId").toString();
        String teetSessionId = pWqInfo.get("teetSessionId").toString();
        String ssoToken = pWqInfo.get("ssoToken").toString();

        String setCookie =  "WMONID=" + wmonId + "; "
                + "NTS_LOGIN_SYSTEM_CODE_P=TXPP; "
                + "NTS_REQUEST_SYSTEM_CODE_P=TXPP; "
                + "TXPPsessionID=" + txppSessionId + ";"
                + "TEETsessionID=" + teetSessionId + ";"
                + "NetFunnel_ID=;";

        //Request Body 값
        String paramData = "<map id='postParam'><ssoToken>";
        paramData += ssoToken;
        paramData += "</ssoToken><userClCd>02</userClCd><popupYn>false</popupYn></map>";

        try {
            String urlStr = "https://teet.hometax.go.kr/permission.do?screenId=UTEETBDA01&domain=hometax.go.kr";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if(conn != null){
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-type", "application/xml");
                conn.setRequestProperty("Cookie", setCookie);
                conn.setRequestProperty("Content-Length",String.valueOf(paramData.getBytes().length));
                conn.setDoInput(true);
                conn.setDoOutput(true);

                //요청 데이터 보내기
                DataOutputStream dataOutputstr = new DataOutputStream(conn.getOutputStream());
                dataOutputstr.writeBytes(paramData);
                dataOutputstr.flush(); //데이터 스트림의 내용을 출력.

                // 응답 헤더(Header) 쿠키의 WMOID, TXPPsessionID 정보 획득
                Map<String, List<String>> header = conn.getHeaderFields();

                String rtTeetSessionId = "";

                if(header.containsKey("Set-Cookie")){

                    List<String> cookie = header.get("Set-Cookie");

                    for(int i = 0; i < cookie.size(); i++){

                        String[] element = cookie.get(i).split(";");

                        for(int j = 0; j < element.length ; j++){
                            if("TEETsessionID".equals(element[j].split("=")[0])){
                                rtTeetSessionId = element[j].split("=")[1];
                            }
                        }
                    }
                }

                dataOutputstr.close();

                rst = rtTeetSessionId;

                // 접속 해제
                conn.disconnect();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
            //e.printStackTrace();
        }

        return rst;
    }

    /**
     * NetFunnelId 취득 함수 (전자세금 계산서 조회 위한 ID)
     * @param pWqInfo
     * @return
     * @throws Exception
     */
    public String urlHometaxNetFunnelId(Map pWqInfo) throws Exception {
        String rst = "";

        //Request Cookie 값
        String wmonId = pWqInfo.get("wmonId").toString();
        String txppSessionId = pWqInfo.get("txppSessionId").toString();
        String teetSessionId = pWqInfo.get("teetSessionId").toString();

        String setCookie =  "NTS_LOGIN_SYSTEM_CODE_P=TXPP; "
                + "NTS_REQUEST_SYSTEM_CODE_P=TEET; "
                + "TXPPsessionID=" + txppSessionId + ";"
                + "TEETsessionID=" + teetSessionId + ";";

        try {
            String urlStr = "https://apct.hometax.go.kr/ts.wseq?opcode=5101&nfid=0&prefix=NetFunnel.gRtype=5101;&sid=service_2&aid=UTEETBDA01&js=yes";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if(conn != null){
                conn.setRequestMethod("GET");

                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setRequestProperty("Cookie", setCookie);

                conn.setDoInput(true);
                conn.getResponseCode();

                // 응답 내용(BODY)의 tin 값 구하기
                InputStream instr = conn.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(instr));

                String temp;
                String netFunnelId = "";
                while((temp = br.readLine()) != null){
                    if(temp.contains("NetFunnel.gRtype=5101;NetFunnel.gControl.result='")){
                        netFunnelId =temp.split("NetFunnel.gRtype=5101;NetFunnel.gControl.result='")[1].split("'; NetFunnel.gControl._showResult()")[0];
                    }
                }
                //dataOutputstr.close();
                // 접속 해제
                conn.disconnect();

                rst = netFunnelId;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
            //e.printStackTrace();
        }

        return rst;
    }

    /**
     * 전자세금 계산서 조회
     * @param pWqInfo
     * @return
     * @throws Exception
     */
    public String urlHometaxSchTaxBill(Map pWqInfo,int pPageNum) throws Exception {

        Date today = new Date();
        Locale currentLocale = new Locale("KOREAN", "KOREA");
        String toDate = "yyyyMMdd";
        SimpleDateFormat formatter = new SimpleDateFormat(toDate,currentLocale);

        String schStDt = formatter.format(DateUtils.addDays(today,-30));//현재일자 - 30
        String schEndDt = formatter.format(today);//현재일자
        String prhSlsClCd = "02";//구분 01: 매출, 02: 매입

        int pageSize = 50;

        String taxList = "";

        //Request Cookie 값
        String wmonId = pWqInfo.get("wmonId").toString();
        String txppSessionId = pWqInfo.get("txppSessionId").toString();
        String teetSessionId = pWqInfo.get("teetSessionId").toString();
        String netFunnelId = pWqInfo.get("netFunnelId").toString();
        String tin = pWqInfo.get("tin").toString();

        String setCookie =  "WMONID=" + wmonId + "; "
                + "NTS_LOGIN_SYSTEM_CODE_P=TXPP; "
                + "TXPPsessionID=" + txppSessionId + ";"
                + "NTS_REQUEST_SYSTEM_CODE_P=TEET; "
                + "TEETsessionID=" + teetSessionId + ";"
                + "NetFunnel_ID=" + URLEncoder.encode(netFunnelId, "UTF-8") + ";";

        //Request Body 값
        String paramData = "<map id=\"ATEETBDA001R01\">" +
                "               <icldLsatInfr>N</icldLsatInfr>" +
                "               <resnoSecYn>Y</resnoSecYn>" +
                "               <srtClCd>1</srtClCd>" +
                "               <srtOpt>01</srtOpt>" +
                "               <map id=\"pageInfoVO\">" +
                "                   <pageSize>" + pageSize + "</pageSize>" +
                "                   <pageNum>" + pPageNum + "</pageNum>" +
                "               </map>" +
                "               <map id=\"excelPageInfoVO\"/>" +
                "               <map id=\"etxivIsnBrkdTermDVOPrmt\">\n" +
                "                       <tnmNm/>" +
                "                       <prhSlsClCd>" + prhSlsClCd + "</prhSlsClCd>" +
                "                       <dtCl>01</dtCl>" +
                "                       <bmanCd>01</bmanCd>" +
                "                       <etxivClsfCd>all</etxivClsfCd>" +
                "                       <isnTypeCd>all</isnTypeCd>" +
                "                       <pageSize>" + pageSize + "</pageSize>" +
                "                       <splrTin></splrTin>" +
                "                       <dmnrTin>" + tin + "</dmnrTin>" +
                "                       <cstnBmanTin></cstnBmanTin>" +
                "                       <splrTxprDscmNo></splrTxprDscmNo>" +
                "                       <dmnrTxprDscmNo></dmnrTxprDscmNo>" +
                "                       <splrMpbNo></splrMpbNo>" +
                "                       <dmnrMpbNo></dmnrMpbNo>" +
                "                       <cstnBmanMpbNo></cstnBmanMpbNo>" +
                "                       <etxivClCd>01</etxivClCd>" +
                "                       <etxivKndCd>all</etxivKndCd>" +
                "                       <inqrDtStrt>" + schStDt + "</inqrDtStrt>" +
                "                       <inqrDtEnd>" + schEndDt + "</inqrDtEnd>" +
                "               </map>" +
                "           </map>";

        try {

            String urlStr = "https://teet.hometax.go.kr/wqAction.do?actionId=ATEETBDA001R01&screenId=UTEETBDA01&popupYn=false&realScreenId=\n";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if(conn != null){
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-type", "application/xml");
                conn.setRequestProperty("Cookie", setCookie);
                conn.setRequestProperty("Content-Length",String.valueOf(paramData.getBytes().length));
                conn.setDoInput(true);
                conn.setDoOutput(true);

                //요청 데이터 보내기
                DataOutputStream dataOutputstr = new DataOutputStream(conn.getOutputStream());
                dataOutputstr.writeBytes(paramData);
                dataOutputstr.flush(); //데이터 스트림의 내용을 출력.

                // 응답 내용(BODY)의 tin 값 구하기
                InputStream instr = conn.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(instr, "UTF-8"));

                String temp;
                int totCnt = 0;

                while((temp = br.readLine()) != null){
                    System.out.println(temp);

                    if(temp.contains("<wTotaCnt>") && pPageNum == 1){
                        totCnt = Integer.parseInt(temp.split("<wTotaCnt>")[1].split("</wTotaCnt>")[0]);
                    }

                    if(temp.contains("<list id='etxivIsnBrkdTermDVOList'>")){
                        taxList = temp.split("<list id='etxivIsnBrkdTermDVOList'>")[1].split("</list>")[0];
                    }

                }

                System.out.println("=========================================");
                System.out.println("=========================================");
                System.out.println("=========================================");

                System.out.println(taxList);

                System.out.println("=========================================totCnt = " + totCnt);

                // 접속 해제
                dataOutputstr.close();
                conn.disconnect();

                //페이지 수에 따른 함수호출(재귀함수)
                int pageCnt = (int)Math.ceil((double)totCnt/pageSize);

                if(pageCnt > 1){
                    for(int j = pageCnt ;  1 < j; j--)
                        taxList += urlHometaxSchTaxBill(pWqInfo, j);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
            //e.printStackTrace();
        }

        return taxList;
    }

    /**
     * 세금계산서 xml 파일 다운로드(jsoup 사용)
     * @param pWqInfo
     * @param taxList
     * @throws Exception
     */
    public List<String> downLoadTaxBillXml(Map pWqInfo, String taxList) throws Exception {

        List<String> taxIssueIdList = new ArrayList<String>();

        String urlStr = "https://teet.hometax.go.kr/wqAction.do";

        //Request Cookie 값
        String wmonId = pWqInfo.get("wmonId").toString();
        String txppSessionId = pWqInfo.get("txppSessionId").toString();
        String teetSessionId = pWqInfo.get("teetSessionId").toString();

        String setCookie =  "WMONID=" + wmonId + "; "
                + "NTS_LOGIN_SYSTEM_CODE_P=TXPP; "
                + "TXPPsessionID=" + txppSessionId + ";"
                + "NTS_REQUEST_SYSTEM_CODE_P=TEET; "
                + "TEETsessionID=" + teetSessionId + ";"
                + "NetFunnel_ID=;";

        try {

            //문자열 Jsoup 사용 형식에 맞도록 변환 [START]
            Document doc = Jsoup.parse(taxList);
            Elements taxEtans = doc.select("etan");
            Elements taxDmnrtins = doc.select("dmnrtin");
            //문자열 Jsoup 사용 형식에 맞도록 변환 [END]

            int taxCnt = taxEtans.size();

            String etan = "";
            String etxivTin = "";

            int downCnt = 0;

            for(int i = 0; i < taxCnt; i++){

                String eleEtan = taxEtans.get(i).toString();
                String eleEtxivTin = taxDmnrtins.get(i).toString();

                etan = eleEtan.split("<etan>")[1].split("</etan>")[0].replace("-","").trim();
                etxivTin = eleEtxivTin.split("<dmnrtin>")[1].split("</dmnrtin>")[0].trim();

                //이미 이전에 받은 파일인 경우 SKIP
                File file = new File(xmlFileDir + etan + ".xml");

                if(!file.exists()){

                    taxIssueIdList.add(etan);

                    //Request Body 값
                    String downloadParam = "<map id=\"ATEETBDA001R02\">" +
                            "             <fileDwnYn>Y</fileDwnYn>" +
                            "             <etan>" + etan + "</etan>" +
                            "             <map id=\"etxivIsnBrkdTermDVOPrmt\">" +
                            "                 <etan>" + etan + "</etan>" +
                            "                 <screenId>UTEETBDA01</screenId>" +
                            "                 <pageNum>1</pageNum>" +
                            "                 <slsPrhClCd>02</slsPrhClCd>" +
                            "                 <etxivClCd></etxivClCd>" +
                            "                 <etxivTin>" + etxivTin + "</etxivTin>" +
                            "                 <etxivMpbNo>0</etxivMpbNo>" +
                            "             </map>" +
                            "         </map>";

                    String paramData = URLEncoder.encode("actionId", "UTF-8") + "=" + URLEncoder.encode("ATEETBDA001R02", "UTF-8");
                    paramData += "&" + URLEncoder.encode("screenId", "UTF-8") + "=" + URLEncoder.encode("UTEETBDA38", "UTF-8");
                    paramData += "&" + URLEncoder.encode("noopen", "UTF-8") + "=" + URLEncoder.encode("false", "UTF-8");
                    paramData += "&" + URLEncoder.encode("downloadView", "UTF-8") + "=" + URLEncoder.encode("Y", "UTF-8");
                    paramData += "&" + URLEncoder.encode("downloadParam", "UTF-8") + "=" + URLEncoder.encode(downloadParam, "UTF-8");

                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                    if(conn != null){
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
                        conn.setRequestProperty("Cookie", setCookie);
                        conn.setRequestProperty("Content-Length",String.valueOf(paramData.getBytes().length));
                        conn.setDoInput(true);
                        conn.setDoOutput(true);

                        //요청 데이터 보내기
                        DataOutputStream dataOutputstr = new DataOutputStream(conn.getOutputStream());
                        dataOutputstr.writeBytes(paramData);
                        dataOutputstr.flush(); //데이터 스트림의 내용을 출력.

                        // 응답 내용(BODY)의 tin 값 구하기
                        InputStream is = conn.getInputStream();

                        FileOutputStream os = new FileOutputStream(new File(xmlFileDir, etan + ".xml"));

                        final int BUFFER_SIZE = 4096;
                        int bytesRead;
                        byte[] buffer = new byte[BUFFER_SIZE];
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }

                        os.close();
                        is.close();

                        // 접속 해제
                        dataOutputstr.close();
                        conn.disconnect();
                    }
                }

                downCnt++;

                if(downCnt == 30) {
                    Thread.sleep(60000);
                    downCnt = 0;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
            //e.printStackTrace();
        }

        return taxIssueIdList;
    }

    /**
     * 세금계산서 xml 파일 다운로드(jsoup 미사용)
     * @param pWqInfo
     * @param taxList
     * @throws Exception
     */
    public void downLoadTaxBillXmlWithoutJsoup(Map pWqInfo, String taxList) throws Exception {

        String urlStr = "https://teet.hometax.go.kr/wqAction.do";

        //Request Cookie 값
        String wmonId = pWqInfo.get("wmonId").toString();
        String txppSessionId = pWqInfo.get("txppSessionId").toString();
        String teetSessionId = pWqInfo.get("teetSessionId").toString();

        String setCookie =  "WMONID=" + wmonId + "; "
                + "NTS_LOGIN_SYSTEM_CODE_P=TXPP; "
                + "TXPPsessionID=" + txppSessionId + ";"
                + "NTS_REQUEST_SYSTEM_CODE_P=TEET; "
                + "TEETsessionID=" + teetSessionId + ";"
                + "NetFunnel_ID=;";

        try {

            int taxCnt = taxList.split("<map id='").length - 1;

            String etan = "";
            String etxivTin = "";

            for(int i = 0; i < taxCnt; i++){

                String taxMapId = "<map id='" + i + "' >";

                String taxInfo = taxList.split(taxMapId)[1].split("</map>")[0];

                if(taxInfo.contains("<etan>")){
                    etan = taxInfo.split("<etan>")[1].split("</etan>")[0].replace("-","");
                }

                if(taxInfo.contains("<dmnrTin>")){
                    etxivTin = taxInfo.split("<dmnrTin>")[1].split("</dmnrTin>")[0];
                }

                //Request Body 값
                String downloadParam = "<map id=\"ATEETBDA001R02\">" +
                        "             <fileDwnYn>Y</fileDwnYn>" +
                        "             <etan>" + etan + "</etan>" +
                        "             <map id=\"etxivIsnBrkdTermDVOPrmt\">" +
                        "                 <etan>" + etan + "</etan>" +
                        "                 <screenId>UTEETBDA01</screenId>" +
                        "                 <pageNum>1</pageNum>" +
                        "                 <slsPrhClCd>02</slsPrhClCd>" +
                        "                 <etxivClCd></etxivClCd>" +
                        "                 <etxivTin>" + etxivTin + "</etxivTin>" +
                        "                 <etxivMpbNo>0</etxivMpbNo>" +
                        "             </map>" +
                        "         </map>";

                String paramData = URLEncoder.encode("actionId", "UTF-8") + "=" + URLEncoder.encode("ATEETBDA001R02", "UTF-8");
                paramData += "&" + URLEncoder.encode("screenId", "UTF-8") + "=" + URLEncoder.encode("UTEETBDA38", "UTF-8");
                paramData += "&" + URLEncoder.encode("noopen", "UTF-8") + "=" + URLEncoder.encode("false", "UTF-8");
                paramData += "&" + URLEncoder.encode("downloadView", "UTF-8") + "=" + URLEncoder.encode("Y", "UTF-8");
                paramData += "&" + URLEncoder.encode("downloadParam", "UTF-8") + "=" + URLEncoder.encode(downloadParam, "UTF-8");

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                if(conn != null){
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
                    conn.setRequestProperty("Cookie", setCookie);
                    conn.setRequestProperty("Content-Length",String.valueOf(paramData.getBytes().length));
                    conn.setDoInput(true);
                    conn.setDoOutput(true);

                    //요청 데이터 보내기
                    DataOutputStream dataOutputstr = new DataOutputStream(conn.getOutputStream());
                    dataOutputstr.writeBytes(paramData);
                    dataOutputstr.flush(); //데이터 스트림의 내용을 출력.

                    // 응답 내용(BODY)의 tin 값 구하기
                    InputStream is = conn.getInputStream();

                    FileOutputStream os = new FileOutputStream(new File(xmlFileDir, etan + ".xml"));

                    final int BUFFER_SIZE = 4096;
                    int bytesRead;
                    byte[] buffer = new byte[BUFFER_SIZE];
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }

                    os.close();
                    is.close();

                    // 접속 해제
                    dataOutputstr.close();
                    conn.disconnect();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
            //e.printStackTrace();
        }
    }

    /**
     * 랜덤 스트링 생성
     * @param length
     * @return
     */
    private String getNtsGenerateRandomString(int length) {
        String seed = "qwertyuiopasdfghjklzxxcvbnm0123456789QWERTYUIOPASDDFGHJKLZXCVBNBM";
        String result = "";

        for (int i = 0; i < length; i++) {
            Double d = Math.floor(Math.random() * seed.length());
            result += seed.charAt(d.intValue());
        }

        return result;
    }
}
