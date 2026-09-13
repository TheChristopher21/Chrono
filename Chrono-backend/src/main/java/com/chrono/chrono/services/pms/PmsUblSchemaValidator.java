package com.chrono.chrono.services.pms;
import org.springframework.stereotype.Component;
import org.w3c.dom.ls.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.*;
import java.io.*;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
/** Validates against the unmodified official UBL 2.1 schemas; imports resolve only bundled resources. */
@Component
public class PmsUblSchemaValidator {
 private static final String ROOT="https://schemas.chrono.invalid/pms/ubl21/";
 private final ConcurrentHashMap<Boolean,Schema> schemas=new ConcurrentHashMap<>();
 public void validate(byte[] document,boolean credit){try{Validator validator=schemas.computeIfAbsent(credit,this::schema).newValidator();validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");validator.validate(new StreamSource(new ByteArrayInputStream(document)));}catch(Exception failure){throw new IllegalArgumentException("UBL-Schemaprüfung fehlgeschlagen: "+failure.getMessage(),failure);}}
 private Schema schema(boolean credit){try{SchemaFactory factory=SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");DOMImplementationLS implementation=(DOMImplementationLS)DocumentBuilderFactory.newInstance().newDocumentBuilder().getDOMImplementation().getFeature("LS","3.0");
  factory.setResourceResolver((type,namespace,publicId,systemId,baseURI)->{URI uri=URI.create(baseURI).resolve(systemId).normalize();if(!uri.toString().startsWith(ROOT))throw new IllegalArgumentException("Nicht mitgeliefertes XML-Schema abgewiesen.");String path=uri.toString().substring(ROOT.length());InputStream stream=getClass().getResourceAsStream("/pms/ubl21/"+path);if(stream==null)throw new IllegalArgumentException("Mitgeliefertes XML-Schema fehlt: "+path);LSInput input=implementation.createLSInput();input.setSystemId(uri.toString());input.setBaseURI(uri.toString());input.setByteStream(stream);return input;});
  String file="xsd/maindoc/UBL-"+(credit?"CreditNote":"Invoice")+"-2.1.xsd";try(InputStream input=getClass().getResourceAsStream("/pms/ubl21/"+file)){if(input==null)throw new IllegalStateException("UBL-Schema fehlt.");return factory.newSchema(new StreamSource(input,ROOT+file));}
 }catch(Exception failure){throw new IllegalStateException("Offizielles UBL-Schema konnte nicht geladen werden.",failure);}}
}
