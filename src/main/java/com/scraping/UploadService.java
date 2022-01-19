package com.scraping;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UploadService {

    /**
     * upload 세금계산서 xml 파일 파싱
     * @throws Exception
     */
    public List<Map<String, Object>> parsingTaxXmlFile(String xmlFileRoot) throws Exception {

        TaxParsingService taxParsingService = new TaxParsingService("");

        List<Map<String, Object>> rst = new ArrayList<>();

        try {
            File fXmlFile = new File(xmlFileRoot);

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setIgnoringElementContentWhitespace(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(fXmlFile);

            doc.getDocumentElement().normalize();

            System.out.println("Root element :" + doc.getDocumentElement().getNodeName());

            //[STEP-1] 관리정보 (Exchanged Document)
            NodeList exchDocList = doc.getElementsByTagName("ExchangedDocument");
            //Map<String, Object> exchMap = this.parseExchangedDocument(exchDocList);

            Map<String, Object> exchMap = taxParsingService.parseExchangedDocument(exchDocList);

            //[STEP-2] 기본 정보(TaxInvoice Document)
            NodeList taxInvDocList = doc.getElementsByTagName("TaxInvoiceDocument");
            Map<String, Object> taxInv = taxParsingService.parseTaxInvoiceDocument(taxInvDocList);

            //[STEP-3] 계산서 정보(TaxInvoice Trade Settlement)
            NodeList invoicerPartyList = doc.getElementsByTagName("InvoicerParty");
            NodeList invoiceePartyList = doc.getElementsByTagName("InvoiceeParty");
            NodeList brokerPartyList = doc.getElementsByTagName("BrokerParty");
            NodeList paymentMeansList = doc.getElementsByTagName("SpecifiedPaymentMeans");
            NodeList monetarySumList = doc.getElementsByTagName("SpecifiedMonetarySummation");

            Map<String, Object> taxInvSett = taxParsingService.parseTaxInvoiceTradeSettlement(invoicerPartyList
                    , invoiceePartyList
                    , brokerPartyList
                    , paymentMeansList
                    , monetarySumList);

            //[STEP-4] 상품 정보(TaxInvoice TradeLine Item)
            NodeList taxInvTrdLineItemList = doc.getElementsByTagName("TaxInvoiceTradeLineItem");
            List<Map<String, Object>> taxInvItem = taxParsingService.parseTaxInvoiceTradeLineItem(taxInvTrdLineItemList);

            System.out.println("===================================================");
            System.out.println("====================PARSING END====================");
            System.out.println("===================================================");

            //[STEP-5] 파싱 결과 값 리턴
            Map<String, Object> parseMap = new HashMap<>();

            parseMap.put("exch", exchMap);
            parseMap.put("taxInv", taxInv);
            parseMap.put("taxInvSett", taxInvSett);
            parseMap.put("taxInvItem", taxInvItem);

            rst.add(parseMap);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return rst;
    }
}
