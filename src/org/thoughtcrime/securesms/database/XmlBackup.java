package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.io.ByteStreams;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.mms.PartUriParser;
import org.thoughtcrime.securesms.util.Base64;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlBackup {

  private static final String PROTOCOL       = "protocol";
  private static final String ADDRESS        = "address";
  private static final String DATE           = "date";
  private static final String TYPE           = "type";
  private static final String SUBJECT        = "subject";
  private static final String BODY           = "body";
  private static final String SERVICE_CENTER = "service_center";
  private static final String READ           = "read";
  private static final String STATUS         = "status";
  private static final String TOA            = "toa";
  private static final String SC_TOA         = "sc_toa";
  private static final String LOCKED         = "locked";

  private final XmlPullParser parser;

  public XmlBackup(String path) throws XmlPullParserException, FileNotFoundException {
    this.parser = XmlPullParserFactory.newInstance().newPullParser();
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
    parser.setInput(new FileInputStream(path), null);
  }

  public XmlBackupItem getNext() throws IOException, XmlPullParserException {
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (parser.getEventType() != XmlPullParser.START_TAG) {
        continue;
      }

      String name = parser.getName();

      if (!name.equalsIgnoreCase("sms")) {
        continue;
      }

      int attributeCount = parser.getAttributeCount();

      if (attributeCount <= 0) {
        continue;
      }

      XmlBackupItem item = new XmlBackupItem();

      for (int i=0;i<attributeCount;i++) {
        String attributeName = parser.getAttributeName(i);

        if      (attributeName.equals(PROTOCOL      )) item.protocol      = Integer.parseInt(parser.getAttributeValue(i));
        else if (attributeName.equals(ADDRESS       )) item.address       = parser.getAttributeValue(i);
        else if (attributeName.equals(DATE          )) item.date          = Long.parseLong(parser.getAttributeValue(i));
        else if (attributeName.equals(TYPE          )) item.type          = Integer.parseInt(parser.getAttributeValue(i));
        else if (attributeName.equals(SUBJECT       )) item.subject       = parser.getAttributeValue(i);
        else if (attributeName.equals(BODY          )) item.body          = parser.getAttributeValue(i);
        else if (attributeName.equals(SERVICE_CENTER)) item.serviceCenter = parser.getAttributeValue(i);
        else if (attributeName.equals(READ          )) item.read          = Integer.parseInt(parser.getAttributeValue(i));
        else if (attributeName.equals(STATUS        )) item.status        = Integer.parseInt(parser.getAttributeValue(i));
      }

      return item;
    }

    return null;
  }

  public static class XmlBackupItem {
    private int    protocol;
    private String address;
    private long   date;
    private int    type;
    private String subject;
    private String body;
    private String serviceCenter;
    private int    read;
    private int    status;

    public XmlBackupItem() {}

    public XmlBackupItem(int protocol, String address, long date, int type, String subject,
                         String body, String serviceCenter, int read, int status)
    {
      this.protocol      = protocol;
      this.address       = address;
      this.date          = date;
      this.type          = type;
      this.subject       = subject;
      this.body          = body;
      this.serviceCenter = serviceCenter;
      this.read          = read;
      this.status        = status;
    }

    public int getProtocol() {
      return protocol;
    }

    public String getAddress() {
      return address;
    }

    public long getDate() {
      return date;
    }

    public int getType() {
      return type;
    }

    public String getSubject() {
      return subject;
    }

    public String getBody() {
      return body;
    }

    public String getServiceCenter() {
      return serviceCenter;
    }

    public int getRead() {
      return read;
    }

    public int getStatus() {
      return status;
    }
  }

  public static class Writer {

    private static final String  XML_HEADER      = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>";
    private static final String  CREATED_BY      = "<!-- File Created By Signal -->";
    private static final String  OPEN_TAG_SMSES  = "<smses count=\"%d\">";
    private static final String  CLOSE_TAG_SMSES = "</smses>";
    private static final String  OPEN_TAG_SMS    = " <sms ";
    private static final String  CLOSE_EMPTYTAG  = "/>";
    private static final String  OPEN_ATTRIBUTE  = "=\"";
    private static final String  CLOSE_ATTRIBUTE = "\" ";

    private static final Pattern PATTERN         = Pattern.compile("[^\u0020-\uD7FF]");

    private final BufferedWriter bufferedWriter;

    public Writer(String path, int count) throws IOException {
      bufferedWriter = new BufferedWriter(new FileWriter(path, false));

      bufferedWriter.write(XML_HEADER);
      bufferedWriter.newLine();
      bufferedWriter.write(CREATED_BY);
      bufferedWriter.newLine();
      bufferedWriter.write(String.format(OPEN_TAG_SMSES, count));
    }

    public void writeItem(XmlBackupItem item) throws IOException {
      StringBuilder stringBuilder = new StringBuilder();

      stringBuilder.append(OPEN_TAG_SMS);
      appendAttribute(stringBuilder, PROTOCOL, item.getProtocol());
      appendAttribute(stringBuilder, ADDRESS, escapeXML(item.getAddress()));
      appendAttribute(stringBuilder, DATE, item.getDate());
      appendAttribute(stringBuilder, TYPE, item.getType());
      appendAttribute(stringBuilder, SUBJECT, escapeXML(item.getSubject()));
      appendAttribute(stringBuilder, BODY, escapeXML(item.getBody()));
      appendAttribute(stringBuilder, TOA, "null");
      appendAttribute(stringBuilder, SC_TOA, "null");
      appendAttribute(stringBuilder, SERVICE_CENTER, item.getServiceCenter());
      appendAttribute(stringBuilder, READ, item.getRead());
      appendAttribute(stringBuilder, STATUS, item.getStatus());
      appendAttribute(stringBuilder, LOCKED, 0);
      stringBuilder.append(CLOSE_EMPTYTAG);

      bufferedWriter.newLine();
      bufferedWriter.write(stringBuilder.toString());
    }

    public void writeMms(Context ctx, MasterSecret secret, MediaMmsMessageRecord record) throws IOException {
      StringBuilder stringBuilder = new StringBuilder();

      stringBuilder.append("<mms ");
      stringBuilder.append("text_only=\"0\" sub=\"null\" retr_st=\"null\" ");
      appendAttribute(stringBuilder, DATE, record.getDateSent());
      stringBuilder.append("ct_cls=\"null\" sub_cs=\"null\" ");
      appendAttribute(stringBuilder, READ, "1"); // read from Read database
      stringBuilder.append("ct_l=\"null\" tr_id=\"null\" st=\"null\" msg_box=\"1\" ");
      appendAttribute(stringBuilder, ADDRESS, escapeXML(record.getIndividualRecipient().getNumber()));
      stringBuilder.append("m_cls=\"personal\" d_tm=\"null\" read_status=\"null\" ct_t=\"application/vnd.wap.multipart.related\" retr_txt_cs=\"null\" d_rpt=\"129\" m_id=\"null\" date_sent=\"0\" seen=\"0\" m_type=\"132\" v=\"18\" exp=\"null\" pri=\"129\" rr=\"129\" resp_txt=\"null\" rpt_a=\"null\" locked=\"0\" retr_txt=\"null\" resp_st=\"null\" m_size=\"null\" readable_date=\"null\" contact_name=\"null\"");
      stringBuilder.append(">\n");

      stringBuilder.append("<part seq=\"0\" ct=\"text/plain\" name=\"Text_0.txt\" chset=\"106\" cd=\"null\" fn=\"null\" cid=\"&lt;313&gt;\" cl=\"Text_0.txt\" ctt_s=\"null\" ctt_t=\"null\" ");
      appendAttribute(stringBuilder, "text", escapeXML(record.getDisplayBody().toString()));

      stringBuilder.append("/>\n");

      stringBuilder.append("<part seq=\"-1\" ct=\"application/smil\" name=\"Smil.txt\" chset=\"106\" cd=\"null\" fn=\"null\" cid=\"&lt;0000&gt;\" cl=\"Smil.txt\" ctt_s=\"null\" ctt_t=\"null\" text='&lt;smil&gt;&#13;&#10;  &lt;head&gt;&#13;&#10;    &lt;layout&gt;&#13;&#10;      &lt;region fit=\"scroll\" height=\"50%\" id=\"Text\" left=\"0\" top=\"50%\" width=\"100%\"/&gt;&#13;&#10;      &lt;region fit=\"meet\" height=\"50%\" id=\"Image\" left=\"0\" top=\"0\" width=\"100%\"/&gt;&#13;&#10;    &lt;/layout&gt;&#13;&#10;  &lt;/head&gt;&#13;&#10;  &lt;body&gt;&#13;&#10;    &lt;par dur=\"5000ms\"&gt;&#13;&#10;      &lt;img region=\"Image\" src=\"cid:312\"/&gt;&#13;&#10;      &lt;text region=\"Text\" src=\"cid:313\"/&gt;&#13;&#10;    &lt;/par&gt;&#13;&#10;  &lt;/body&gt;&#13;&#10;&lt;/smil&gt;&#13;&#10;' />\n");

      for (Attachment att: record.getSlideDeck().asAttachments()) {
        stringBuilder.append("<part seq=\"0\" ");
        appendAttribute(stringBuilder, "ct", att.getContentType());
        stringBuilder.append("name=\"test.jpeg\" chset=\"null\" cd=\"null\" fn=\"null\" cid=\"&lt;312&gt;\" cl=\"test.jpeg\" ctt_s=\"null\" ctt_t=\"null\" text=\"null\" ");

        PartUriParser partUri = new PartUriParser(att.getDataUri());
        InputStream inputData = DatabaseFactory.getAttachmentDatabase(ctx).getAttachmentStream(secret, partUri.getPartId());

        byte[] buffer = new byte[1000000];
        int read = inputData.read(buffer);
        if (read == buffer.length) throw new IllegalStateException("Too small buffer"); // FIXME
        appendAttribute(stringBuilder, "data", Base64.encodeBytes(buffer, 0, read));
        inputData.close();

        stringBuilder.append("/>\n");
      }

      stringBuilder.append("</mms>");

      /*
    <mms text_only="0" sub="null" retr_st="null" date="1406695762000" ct_cls="null" sub_cs="null" read="1" ct_l="null" tr_id="null" st="null" msg_box="1" address="0444444444" m_cls="personal" d_tm="null" read_status="null" ct_t="application/vnd.wap.multipart.related" retr_txt_cs="null" d_rpt="129" m_id="mcG91w-iGJVjJh5EIgcp00@mmsc.mdata.net.au" date_sent="0" seen="0" m_type="132" v="18" exp="null" pri="129" rr="129" resp_txt="null" rpt_a="null" locked="0" retr_txt="null" resp_st="null" m_size="null" readable_date="30 Jul 2014 14:49:22" contact_name="B">
      <parts>
        <part seq="-1" ct="application/smil" name="Smil.txt" chset="106" cd="null" fn="null" cid="&lt;0000&gt;" cl="Smil.txt" ctt_s="null" ctt_t="null" text='&lt;smil&gt;&#13;&#10;  &lt;head&gt;&#13;&#10;    &lt;layout&gt;&#13;&#10;      &lt;region fit="scroll" height="50%" id="Text" left="0" top="50%" width="100%"/&gt;&#13;&#10;      &lt;region fit="meet" height="50%" id="Image" left="0" top="0" width="100%"/&gt;&#13;&#10;    &lt;/layout&gt;&#13;&#10;  &lt;/head&gt;&#13;&#10;  &lt;body&gt;&#13;&#10;    &lt;par dur="5000ms"&gt;&#13;&#10;      &lt;img region="Image" src="cid:312"/&gt;&#13;&#10;      &lt;text region="Text" src="cid:313"/&gt;&#13;&#10;    &lt;/par&gt;&#13;&#10;  &lt;/body&gt;&#13;&#10;&lt;/smil&gt;&#13;&#10;' />
        <part seq="0" ct="image/jpeg" name="C:DataUsersDefAppsAppDataINTERNETEXPLORERTempSaved   Imagesuntitled.jpg" chset="null" cd="null" fn="null" cid="&lt;312&gt;" cl="C:DataUsersDefAppsAppDataINTERNETEXPLORERTempSaved   Imagesuntitled.jpg" ctt_s="null" ctt_t="null" text="null" data="
        <part seq="0" ct="text/plain" name="Text_0.txt" chset="106" cd="null" fn="null" cid="&lt;313&gt;" cl="Text_0.txt" ctt_s="null" ctt_t="null" text="&#13;&#10;derpy alpaca." />
      </parts>
    </mms>



  CREATE TABLE mms (_id INTEGER PRIMARY KEY, thread_id INTEGER, date INTEGER, date_received INTEGER, msg_box INTEGER, read INTEGER DEFAULT 0, m_id TEXT, sub TEXT, sub_cs INTEGER, body TEXT, part_count INTEGER, ct_t TEXT, ct_l TEXT, address TEXT, address_device_id INTEGER, exp INTEGER, m_cls TEXT, m_type INTEGER, v INTEGER, m_size INTEGER, pri INTEGER, rr INTEGER, rpt_a INTEGER, resp_st INTEGER, st INTEGER, tr_id TEXT, retr_st INTEGER, retr_txt TEXT, retr_txt_cs INTEGER, read_status INTEGER, ct_cls INTEGER, resp_txt TEXT, d_tm INTEGER, delivery_receipt_count INTEGER DEFAULT 0, mismatched_identities TEXT DEFAULT NULL, network_failures TEXT DEFAULT NULL,d_rpt INTEGER, subscription_id INTEGER DEFAULT -1);
  CREATE TABLE pdu (_id INTEGER PRIMARY KEY ,thread_id INTEGER, date INTEGER, date_sent INTEGER    0,msg_box INTEGER, read INTEGER DEFAULT 0, m_id TEXT, sub TEXT, sub_cs INTEGER,                                ct_t TEXT, ct_l TEXT,                                          exp INTEGER, m_cls TEXT, m_type INTEGER, v INTEGER, m_size INTEGER, pri INTEGER, rr INTEGER, rpt_a INTEGER, resp_st INTEGER, st INTEGER, tr_id TEXT, retr_st INTEGER, retr_txt TEXT, retr_txt_cs INTEGER, read_status INTEGER, ct_cls INTEGER, resp_txt TEXT, d_tm INTEGER,                                                                                                                       d_rpt INTEGER, locked INTEGER DEFAULT 0,sub_id INTEGER DEFAULT -1, seen INTEGER DEFAULT 0,creator TEXT,text_only INTEGER DEFAULT 0);

3|4|1463318177360|1463318177365|-2136997865|1||||VITnJSEyD2MtYuxn5VZzQN91Uz4yGngMR8hQniG+7134sgeufp7CS213FxppY3meXbJdOQ==|1|||||||128||||||||||||||||1||||-1

  CREATE TABLE part (_id INTEGER PRIMARY KEY, mid INTEGER, seq INTEGER DEFAULT 0, ct TEXT, name TEXT, chset INTEGER, cd TEXT, fn TEXT, cid TEXT, cl TEXT, ctt_s INTEGER, ctt_t TEXT, encrypted INTEGER, pending_push INTEGER, _data TEXT, data_size INTEGER, thumbnail TEXT, aspect_ratio REAL, unique_id INTEGER NOT NULL);
  CREATE TABLE part (_id INTEGER PRIMARY KEY, mid INTEGER, seq INTEGER DEFAULT 0, ct TEXT, name TEXT, chset INTEGER, cd TEXT, fn TEXT, cid TEXT, cl TEXT, ctt_s INTEGER, ctt_t TEXT,                                          _data TEXT, text TEXT);

4|4|0|image/jpeg||||||||||0|/data/data/org.thoughtcrime.securesms/app_parts/part1395958363.mms|73776|/data/data/org.thoughtcrime.securesms/app_parts/part1919724798.mms|0.750909090042114|1463318259018
       */

      bufferedWriter.newLine();
      bufferedWriter.write(stringBuilder.toString());
    }

    private <T> void appendAttribute(StringBuilder stringBuilder, String name, T value) {
      stringBuilder.append(name).append(OPEN_ATTRIBUTE).append(value).append(CLOSE_ATTRIBUTE);
    }

    public void close() throws IOException {
      bufferedWriter.newLine();
      bufferedWriter.write(CLOSE_TAG_SMSES);
      bufferedWriter.close();
    }

    private String escapeXML(String s) {
      if (TextUtils.isEmpty(s)) return s;

      Matcher matcher = PATTERN.matcher( s.replace("&",  "&amp;")
                                          .replace("<",  "&lt;")
                                          .replace(">",  "&gt;")
                                          .replace("\"", "&quot;")
                                          .replace("'",  "&apos;"));
      StringBuffer st = new StringBuffer();

      while (matcher.find()) {
        String escaped="";
        for (char ch: matcher.group(0).toCharArray()) {
          escaped += ("&#" + ((int) ch) + ";");
        }
        matcher.appendReplacement(st, escaped);
      }
      matcher.appendTail(st);
      return st.toString();
    }

  }
}
