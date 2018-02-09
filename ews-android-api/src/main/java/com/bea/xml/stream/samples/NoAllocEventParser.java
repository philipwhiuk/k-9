package com.bea.xml.stream.samples;

import com.bea.xml.stream.StaticAllocator;
import java.io.FileReader;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import javax.xml.stream.util.*;
import javax.xml.namespace.QName;

/**
 * @author Copyright (c) 2002 by BEA Systems. All Rights Reserved.
 */

public class NoAllocEventParser {
  private static String filename = null;
  
  private static void printUsage() {
    System.out.println("usage: java com.bea.xml.stream.samples.EventParse <xmlfile>");
  }

  public static void main(String[] args) throws Exception {
    try { 
      filename = args[0];
    } catch (ArrayIndexOutOfBoundsException aioobe){
      printUsage();
      System.exit(0);
    }
    System.setProperty("javax.xml.stream.XMLInputFactory", 
                       "com.bea.xml.stream.MXParserFactory");
    System.setProperty("javax.xml.stream.XMLEventFactory", 
                       "com.bea.xml.stream.EventFactory");

    XMLInputFactory factory = XMLInputFactory.newInstance();
    XMLEventAllocator allocator = new StaticAllocator();
    factory.setEventAllocator(allocator);
    XMLEventReader r = 
      factory.createXMLEventReader(new FileReader(filename));
    while(r.hasNext()) {
      XMLEvent e = r.nextEvent();
      System.out.println("ID:"+e.hashCode()+"["+e+"]");
    }
  }
}


