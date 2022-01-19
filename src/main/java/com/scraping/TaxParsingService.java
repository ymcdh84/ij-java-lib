package com.scraping;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaxParsingService {

    String xmlFileDir;

    public TaxParsingService(String xmlFileDir) {
        this.xmlFileDir = xmlFileDir;
    }

    /**
     * 세금계산서 xml 파일 파싱
     * @param taxIssueIdList
     * @throws Exception
     */
    public List<Map<String, Object>> parsingTaxXmlFile(List<String> taxIssueIdList) throws Exception {

        List<Map<String, Object>> rst = new ArrayList<>();

        for(String taxIssueId : taxIssueIdList){
            try {
                File fXmlFile = new File(xmlFileDir + taxIssueId + ".xml");

                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                dbFactory.setIgnoringElementContentWhitespace(true);
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(fXmlFile);

                doc.getDocumentElement().normalize();

                System.out.println("Root element :" + doc.getDocumentElement().getNodeName());

                //[STEP-1] 관리정보 (Exchanged Document)
                NodeList exchDocList = doc.getElementsByTagName("ExchangedDocument");
                Map<String, Object> exchMap = this.parseExchangedDocument(exchDocList);

                //[STEP-2] 기본 정보(TaxInvoice Document)
                NodeList taxInvDocList = doc.getElementsByTagName("TaxInvoiceDocument");
                Map<String, Object> taxInv = this.parseTaxInvoiceDocument(taxInvDocList);

                //[STEP-3] 계산서 정보(TaxInvoice Trade Settlement)
                NodeList invoicerPartyList = doc.getElementsByTagName("InvoicerParty");
                NodeList invoiceePartyList = doc.getElementsByTagName("InvoiceeParty");
                NodeList brokerPartyList = doc.getElementsByTagName("BrokerParty");
                NodeList paymentMeansList = doc.getElementsByTagName("SpecifiedPaymentMeans");
                NodeList monetarySumList = doc.getElementsByTagName("SpecifiedMonetarySummation");

                Map<String, Object> taxInvSett = this.parseTaxInvoiceTradeSettlement(invoicerPartyList
                                                                                        , invoiceePartyList
                                                                                        , brokerPartyList
                                                                                        , paymentMeansList
                                                                                        , monetarySumList);

                //[STEP-4] 상품 정보(TaxInvoice TradeLine Item)
                NodeList taxInvTrdLineItemList = doc.getElementsByTagName("TaxInvoiceTradeLineItem");
                List<Map<String, Object>> taxInvItem = this.parseTaxInvoiceTradeLineItem(taxInvTrdLineItemList);

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
        }

        return rst;
    }

    /**
     * 1. 관리 정보(Exchanged Document) 데이터 파싱
     * @param exchDocList
     * @return
     * @throws Exception
     */
    public Map<String, Object> parseExchangedDocument(NodeList exchDocList) throws Exception {
        Map<String, Object> rst = new HashMap<>();

        Node exchDocNode = exchDocList.item(0);
        Element eElement = (Element) exchDocNode;

        rst.put("exchDocId", getTagValue("ID", eElement));
        rst.put("exchDocReferId", getTagValue("ReferencedDocument", "ID", eElement));
        rst.put("exchDocIssueDateTime", getTagValue("IssueDateTime", eElement));

        return rst;
    }

    /**
     * 2. 기본 정보(TaxInvoice Document) 데이터 파싱
     * @param taxInvDocList
     * @return
     * @throws Exception
     */
    public Map<String, Object> parseTaxInvoiceDocument(NodeList taxInvDocList) throws Exception {
        Map<String, Object> rst = new HashMap<>();

        Node taxInvDocNode = taxInvDocList.item(0);
        Element eElement = (Element) taxInvDocNode;

        rst.put("taxInvDocIssueId", getTagValue("IssueID", eElement));
        rst.put("taxInvDocIssueDateTime", getTagValue("IssueDateTime", eElement));
        rst.put("taxInvDocTypeCode", getTagValue("TypeCode", eElement));
        rst.put("taxInvDocPurposeCode", getTagValue("PurposeCode", eElement));
        rst.put("taxInvDocAmendCode", getTagValue("AmendmentStatusCode", eElement));
        rst.put("taxInvDocOriId", getTagValue("OriginalIssueID", eElement));
        rst.put("taxInvDocDesc", getTagValue("DescriptionText", eElement));
        rst.put("taxInvDocRefId", getTagValue("ReferencedImportDocument", "ID", eElement));
        rst.put("taxInvDocRefItemQuan", getTagValue("ReferencedImportDocument", "ItemQuantity", eElement));
        rst.put("taxInvDocAcceptStDateTime", getTagValue("ReferencedImportDocument", "AcceptablePeriod", "StartDateTime", eElement));
        rst.put("taxInvDocAcceptEndDateTime", getTagValue("ReferencedImportDocument", "AcceptablePeriod", "EndDateTime", eElement));

        return rst;
    }


    /**
     * 3. 계산서 정보(TaxInvoice Trade Settlement) 데이터 파싱
     * @param invoicerPartyList
     * @param invoiceePartyList
     * @param brokerPartyList
     * @param paymentMeansList
     * @param monetarySumList
     * @return
     * @throws Exception
     */
    public Map<String, Object> parseTaxInvoiceTradeSettlement( NodeList invoicerPartyList
                                                             , NodeList invoiceePartyList
                                                             , NodeList brokerPartyList
                                                             , NodeList paymentMeansList
                                                             , NodeList monetarySumList) throws Exception {
        Map<String, Object> rst = new HashMap<>();

        //[STEP-1] 거래처 정보 - 공급자 Parsing
        Node invoicerNode = invoicerPartyList.item(0);
        Element eInvoicerElement = (Element) invoicerNode;

        rst.put("invoicerId", getTagValue("ID", eInvoicerElement));
        rst.put("invoicerTaxRegId", getTagValue("SpecifiedOrganization", "TaxRegistrationID", eInvoicerElement));
        rst.put("invoicerName", getTagValue("NameText", eInvoicerElement));
        rst.put("invoicerSpecifiedPerson", getTagValue("SpecifiedPerson", "NameText", eInvoicerElement));
        rst.put("invoicerSpecifiedAddr", getTagValue("SpecifiedAddress", "LineOneText", eInvoicerElement));
        rst.put("invoicerTypeCode", getTagValue("TypeCode", eInvoicerElement));
        rst.put("invoicerClassificationCode", getTagValue("ClassificationCode", eInvoicerElement));
        rst.put("invoicerDefineDept", getTagValue("DefinedContact", "DepartmentNameText", eInvoicerElement));
        rst.put("invoicerDefinePerson", getTagValue("DefinedContact", "PersonNameText", eInvoicerElement));
        rst.put("invoicerDefineTel", getTagValue("DefinedContact", "TelephoneCommunication", eInvoicerElement));
        rst.put("invoicerDefineUri", getTagValue("DefinedContact", "URICommunication", eInvoicerElement));

        //[STEP-2] 거래처 정보 - 공급받는자 Parsing
        Node invoiceeNode = invoiceePartyList.item(0);
        Element eInvoiceeElement = (Element) invoiceeNode;

        rst.put("invoiceeId", getTagValue("ID", eInvoiceeElement));
        rst.put("invoiceeBusinessTypeCode", getTagValue("SpecifiedOrganization", "BusinessTypeCode", eInvoiceeElement));
        rst.put("invoiceeTaxRegId", getTagValue("SpecifiedOrganization", "TaxRegistrationID", eInvoiceeElement));
        rst.put("invoiceeName", getTagValue("NameText", eInvoiceeElement));
        rst.put("invoiceeSpecifiedPerson", getTagValue("SpecifiedPerson", "NameText", eInvoiceeElement));
        rst.put("invoiceeSpecifiedAddr", getTagValue("SpecifiedAddress", "LineOneText", eInvoiceeElement));
        rst.put("invoiceeTypeCode", getTagValue("TypeCode", eInvoiceeElement));
        rst.put("invoiceeClassificationCode", getTagValue("ClassificationCode", eInvoiceeElement));
        rst.put("invoiceePriDept", getTagValue("PrimaryDefinedContact", "DepartmentNameText", eInvoiceeElement));
        rst.put("invoiceePriPerson", getTagValue("PrimaryDefinedContact", "PersonNameText", eInvoiceeElement));
        rst.put("invoiceePriTel", getTagValue("PrimaryDefinedContact", "TelephoneCommunication", eInvoiceeElement));
        rst.put("invoiceePriUri", getTagValue("PrimaryDefinedContact", "URICommunication", eInvoiceeElement));
        rst.put("invoiceeSecondDept", getTagValue("SecondaryDefinedContact", "DepartmentNameText", eInvoiceeElement));
        rst.put("invoiceeSecondPerson", getTagValue("SecondaryDefinedContact", "PersonNameText", eInvoiceeElement));
        rst.put("invoiceeSecondTel", getTagValue("SecondaryDefinedContact", "TelephoneCommunication", eInvoiceeElement));
        rst.put("invoiceeSecondUri", getTagValue("SecondaryDefinedContact", "URICommunication", eInvoiceeElement));


        //[STEP-3] 거래처 정보 - 수탁자 Parsing
        Node brokerNode = brokerPartyList.item(0);
        Element eBrokerElement = (Element) brokerNode;

        if(eBrokerElement != null){

            rst.put("brokerId", getTagValue("ID", eBrokerElement));
            rst.put("brokerTaxRegId", getTagValue("SpecifiedOrganization", "TaxRegistrationID", eBrokerElement));
            rst.put("brokerName", getTagValue("NameText", eBrokerElement));
            rst.put("brokerSpecifiedPerson", getTagValue("SpecifiedPerson", "NameText", eBrokerElement));
            rst.put("brokerSpecifiedAddr", getTagValue("SpecifiedAddress", "LineOneText", eBrokerElement));
            rst.put("brokerTypeCode", getTagValue("TypeCode", eBrokerElement));
            rst.put("brokerClassificationCode", getTagValue("ClassificationCode", eBrokerElement));
            rst.put("brokerDefineDept", getTagValue("DefinedContact", "DepartmentNameText", eBrokerElement));
            rst.put("brokerDefinePerson", getTagValue("DefinedContact", "PersonNameText", eBrokerElement));
            rst.put("brokerDefineTel", getTagValue("DefinedContact", "TelephoneCommunication", eBrokerElement));
            rst.put("brokerDefineUri", getTagValue("DefinedContact", "URICommunication", eBrokerElement));
        }


        //[STEP-4] 결제정보 - 결제방법별금액
        Node paymentNode = paymentMeansList.item(0);
        Element ePaymentElement = (Element) paymentNode;

        if(ePaymentElement != null){
            rst.put("payTypeCode", getTagValue("TypeCode", ePaymentElement));
            rst.put("payAmt", getTagValue("PaidAmount", ePaymentElement));
        }

        //[STEP-5] 결제정보 - Summary
        Node monetaryNode = monetarySumList.item(0);
        Element eMonetaryElement = (Element) monetaryNode;

        if(eMonetaryElement != null){
            rst.put("chargeTotalAmt", getTagValue("ChargeTotalAmount", eMonetaryElement));
            rst.put("taxTotalAmt", getTagValue("TaxTotalAmount", eMonetaryElement));
            rst.put("grandTotalAmt", getTagValue("GrandTotalAmount", eMonetaryElement));
        }

        return rst;
    }

    /**
     * 4. 상품 정보(TaxInvoice TradeLine Item) 데이터 파싱
     * @param taxInvTrdLineItemList
     * @return
     * @throws Exception
     */
    public List<Map<String, Object>> parseTaxInvoiceTradeLineItem(NodeList taxInvTrdLineItemList) throws Exception {

        List<Map<String, Object>> rst = new ArrayList<>();

        for (int i = 0; i < taxInvTrdLineItemList.getLength(); i++) {

            Map<String, Object> map = new HashMap<>();

            Node nNode = taxInvTrdLineItemList.item(i);

            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;

                map.put("seq", getTagValue("SequenceNumeric", eElement));
                map.put("itemPurchExpDateTime", getTagValue("PurchaseExpiryDateTime", eElement));
                map.put("itemName", getTagValue("NameText", eElement));
                map.put("itemInform", getTagValue("InformationText", eElement));
                map.put("itemDesc", getTagValue("DescriptionText", eElement));
                map.put("itemChargeUnitQuan", getTagValue("ChargeableUnitQuantity", eElement));
                map.put("itemUnitAmt", getTagValue("UnitPrice", "UnitAmount", eElement));
                map.put("itemAmt", getTagValue("InvoiceAmount", eElement));
                map.put("itemCalcAmt", getTagValue("TotalTax", "CalculatedAmount", eElement));
            }

            rst.add(map);
        }

        return rst;
    }

    /**
     * 자식-자식 [3 Depth] 태그값 리턴 함수
     * @param sPTag
     * @param sCTag
     * @param eElement
     * @return
     */
    private static String getTagValue(String sPTag, String sCTag, String sCCTag, Element eElement) {

        String tagValue = "";

        Node nNode = eElement.getElementsByTagName(sPTag).item(0);

        if(nNode != null){
            NodeList pList = nNode.getChildNodes();

            for(int i = 0; i < pList.getLength(); i++){

                Node cNode = pList.item(i);

                if(cNode.getNodeName().equalsIgnoreCase(sCTag)){
                    NodeList ccList = cNode.getChildNodes();

                    for(int j = 0; j < ccList.getLength(); j++){
                        Node ccNode = ccList.item(j);
                        if(ccNode.getNodeName().equalsIgnoreCase(sCCTag)){
                            tagValue = ccNode.getChildNodes().item(0).getNodeValue().trim();
                        }
                    }
                }
            }
        }

        return tagValue;
    }

    /**
     * 자식 [2 Depth] 태그값 리턴 함수
     * @param sPTag
     * @param sCTag
     * @param eElement
     * @return
     */
    private static String getTagValue(String sPTag, String sCTag, Element eElement) {

        String tagValue = "";

        Node nNode = eElement.getElementsByTagName(sPTag).item(0);

        if(nNode != null){
            NodeList pList = nNode.getChildNodes();

            for(int i = 0; i < pList.getLength(); i++){

                Node cNode = pList.item(i);

                if(cNode.getNodeName().equalsIgnoreCase(sCTag)){
                    tagValue = cNode.getChildNodes().item(0).getNodeValue().trim();
                }
            }
        }

        return tagValue;
    }

    /**
     * 태그값 리턴 함수
     * @param sTag
     * @param eElement
     * @return
     */
    private static String getTagValue(String sTag, Element eElement) {

        String tagValue = "";

        Node nNode = eElement.getElementsByTagName(sTag).item(0);

        if(nNode != null){
            NodeList nlList = nNode.getChildNodes();

            tagValue = nlList.item(0).getNodeValue().trim();
        }

        return tagValue;
    }

    private String stringValueOf(Object object) {
        return object == null ? "" : String.valueOf(object);
    }
}
