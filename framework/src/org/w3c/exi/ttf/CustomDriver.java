/*
 * EXI Testing Task Force Measurement Suite: http://www.w3.org/XML/EXI/
 *
 * Copyright � [2006] World Wide Web Consortium, (Massachusetts Institute of
 * Technology, European Research Consortium for Informatics and Mathematics,
 * Keio University). All Rights Reserved. This work is distributed under the
 * W3C� Software License [1] in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.
 *
 * [1] http://www.w3.org/Consortium/Legal/2002/copyright-software-20021231
 */

package org.w3c.exi.ttf;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.w3c.exi.ttf.parameters.MeasureParam;

import com.sun.japex.TestCase;

/**
 * Base class for EXI compactness and read/write performance drivers.
 * The purpose of this class is to simplify the work needed to implement
 * such a driver, by handling much of the work of setting up, as well
 * as abstracting the various read/write media and measuring modes.
 * A candiate needs only implement a single driver to measure compactness, read
 * throughput, and write throughput to/from memory, various networks or other media.
 *
 * <p>A SubClass MUST implement:<pre>
 *      void prepareTestCase(DriverParameters driverParams, TestCaseParameters testCaseParams)
 *      Event[] createEventsFromXMLTestCase(InputStream xmlInput)
 *      void encode(Event[] inputEvents, OutputStream output)
 *      void decode(InputStream input, ArrayList<Event> outputEvents)
 * </pre></p>
 *
 * <p>A SubClass MAY implement:<pre>
 *      void transcodeTestCase(String xmlfile, OutputStream encodedOutput)
 *      boolean gzipStream()
 * </pre></p>
 *
 * This driver uses the following Japex parameters:
 *
 * <P>Per-driver Japex parameters:
 * <UL>
 * <LI><PRE>org.w3c.exi.ttf.applicationClass</PRE> "neither" / "schema" / "document" / "both"</LI>
 * <LI><PRE>org.w3c.exi.ttf.recordDecodedEvents</PRE> "yes" / "no" - defaults to "yes"</LI>
 * <LI><PRE>org.w3c.exi.ttf.dataSourceSink.URI</PRE> "memory:/", "network://localhost:"+portNumber, network://hostname+":"+portNumber.
 *      If "memory" then input/output is to a local memory. If "localhost:"+portNumber then a network
 *      host is spawned on an in-process thread and input/output is in memory between in-process
 *      threads via the loopback interface. This isolates processing efficiency measurements from
 *      network I/O and the caching effects associated with in-memory testing. If hostname+":"+portNumber
 *      then an instance of NetworkHost must be started on the specified host and input/output is to
 *      the specified host.</LI>
 * </UL>
 *
 *
 * <P>Per-test Japex parameters:
 * <UL>
 * <LI><PRE>japex.inputfile</PRE>the path to the text-xml source.</DD>
 * <LI><PRE>exi.schemaLocation</PRE>the path to the XSD schema file.</DD>
 * <LI><PRE>exi.preserve</PRE>(optional) space seperated list of "PIs" / "ProcessingInstructions" / "Comments" / "Whitespace" / "Prefixes" / "LexicalValues" / "DTDs" / "EntityReferences" / "Notations" (case insensitive)</DD>
 * </UL>
 * 
 * @author AgileDelta
 * @author Sun
 * @author Fujitsu
 * 
 */
public abstract class CustomDriver extends BaseDriver {
    private Event[] _events;
    
    private ArrayList<Event> _eventList;
    
    /**
     * Parse the specified text-xml input stream into a sequence of events 
     * for replay by encode()
     *
     * @param xmlInput
     * @return
     * @throws Exception
     */
    protected abstract Event[] createEventsFromXMLTestCase(InputStream xmlInput) 
    throws Exception;
    
    /**
     * Serialize the specified events to the provided OutputStream
     *
     * @param inputEvents
     *            Events generated by parseText() for replay
     * @param outputStream
     * @throws Exception
     */
    protected abstract void encode(Event[] inputEvents, OutputStream outputStream)
    throws Exception;
    
    /**
     * Parse from the provided input, optionally generating Events into the
     * provided events container.
     *
     * @param inputStream
     *            InputStream to parse from
     * @param outputEvents
     *            potentially null, collector for a log of the parse events
     * @throws Exception
     */
    protected abstract void decode(InputStream inputStream,
            ArrayList<Event> outputEvents)
            throws Exception;
    
    
    /**
     * Encode the given text-xml. A default implementation based on parseText()
     * and encode() is provided. The primary reason why a driver would implement
     * this method is for a text-xml driver, where there is no need to actually
     * parse the input-file (and where the original text representation should
     * be left unchanged).
     *
     * @param xmlInput
     *            The input stream of the text-xml
     * @param encodedOutput
     */
    @Override public void transcodeTestCase(InputStream xmlInput, OutputStream encodedOutput)
    throws Exception {
        Event[] events = createEventsFromXMLTestCase(xmlInput);
        encode(events, encodedOutput);
    }
    
    /**
     * Validate that driver can (re)parse input
     *
     * @param encodedInput
     *            the input stream of the encoded xml
     */
    @Override
    protected void validateStream(InputStream encodedInput, InputStream originalInput, 
				  boolean isIot) throws Exception {
        decode(encodedInput, null);
    }
    
    public final void initializeDriver() {
        // noop
    }
    
    public final void prepare(TestCase testCase) {
        super.prepare(testCase);
        
        try {
            switch(_driverParams.measure) {
                case encode:
                    _events = createEventsFromXMLTestCase(_testCaseParams.getXmlInputStream());
                    break;
                case decode:
                    _eventList = (_testCaseParams.traceRead) 
                    ? new ArrayList<Event>() : null;
                    break;
                default:
            }
        } catch (Exception e) {
            System.err.println("Error preparing test: " + testCase.getName());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    public final void run(TestCase testCase) {
        try {
            if (_driverParams.measure == MeasureParam.decode) {
                if (_eventList != null)
                    _eventList.clear();
                
                decode(_dataSource.getInputStream(), _eventList);
                _dataSource.finish();
            } else if (_driverParams.measure == MeasureParam.encode) {
                encode(_events, _dataSink.getOutputStream());
                _dataSink.finish();
            }
        } catch (Exception e) {
            System.err.println("Error during test run: " + testCase.getName());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
