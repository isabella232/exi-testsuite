/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the  "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: LSSerializerImpl.java,v 1.1 2010/05/07 01:12:08 tkamiya Exp $
 */

package org.apache.xml.serializer.dom3;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.xml.serializer.DOM3Serializer;
import org.apache.xml.serializer.Encodings;
import org.apache.xml.serializer.OutputPropertiesFactory;
import org.apache.xml.serializer.Serializer;
import org.apache.xml.serializer.SerializerFactory;
import org.apache.xml.serializer.utils.MsgKey;
import org.apache.xml.serializer.utils.SystemIDResolver;
import org.apache.xml.serializer.utils.Utils;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMError;
import org.w3c.dom.DOMErrorHandler;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMStringList;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.ls.LSException;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.w3c.dom.ls.LSSerializerFilter;

/**
 * Implemenatation of DOM Level 3 org.w3c.ls.LSSerializer and 
 * org.w3c.dom.ls.DOMConfiguration.  Serialization is achieved by delegating 
 * serialization calls to <CODE>org.apache.xml.serializer.ToStream</CODE> or 
 * one of its derived classes depending on the serialization method, while walking
 * the DOM in DOM3TreeWalker.  
 * @see <a href="http://www.w3.org/TR/2004/REC-DOM-Level-3-LS-20040407/load-save.html#LS-LSSerializer">org.w3c.dom.ls.LSSerializer</a>
 * @see <a href="http://www.w3.org/TR/2004/REC-DOM-Level-3-Core-20040407/core.html#DOMConfiguration">org.w3c.dom.DOMConfiguration</a>
 *  
 * @version $Id:  
 * 
 * @xsl.usage internal 
 */
final public class LSSerializerImpl implements DOMConfiguration, LSSerializer {
    
    // The default end-of-line character sequence used in serialization.
    private static final String DEFAULT_END_OF_LINE;
    static {
        String lineSeparator = (String) AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                try {
                    return System.getProperty("line.separator");
                }
                catch (SecurityException ex) {}
                return null;
            }
        });
        // The DOM Level 3 Load and Save specification requires that implementations choose a default
        // sequence which matches one allowed by XML 1.0 (or XML 1.1). If the value of "line.separator" 
        // isn't one of the XML 1.0 end-of-line sequences then we select "\n" as the default value.
        DEFAULT_END_OF_LINE = lineSeparator != null && 
            (lineSeparator.equals("\r\n") || lineSeparator.equals("\r")) ? lineSeparator : "\n";
    }
    
    /** private data members */
    private Serializer fXMLSerializer = null;
    
    // Tracks DOMConfiguration features. 
    protected int fFeatures = 0;
    
    // Common DOM serializer
    private  DOM3Serializer fDOMSerializer = null;
    
    // A filter set on the LSSerializer
    private LSSerializerFilter fSerializerFilter = null;  
    
    // Stores the nodeArg parameter to speed up multiple writes of the same node.
    private Node fVisitedNode = null;
    
    // The end-of-line character sequence used in serialization. "\n" is whats used on the web.
    private String fEndOfLine = DEFAULT_END_OF_LINE;
    
    // The DOMErrorhandler.
    private DOMErrorHandler fDOMErrorHandler = null;
    
    // The Configuration parameter to pass to the Underlying serilaizer.
    private Properties fDOMConfigProperties = null;
    
    // The encoding to use during serialization.
    private String fEncoding; 
	
    // ************************************************************************
    // DOM Level 3 DOM Configuration parameter names
    // ************************************************************************    
    // Parameter canonical-form, true [optional] - NOT SUPPORTED 
    private final static int CANONICAL = 0x1 << 0;
    
    // Parameter cdata-sections, true [required] (default)
    private final static int CDATA = 0x1 << 1;
    
    // Parameter check-character-normalization, true [optional] - NOT SUPPORTED 
    private final static int CHARNORMALIZE = 0x1 << 2;
    
    // Parameter comments, true [required] (default)
    private final static int COMMENTS = 0x1 << 3;
    
    // Parameter datatype-normalization, true [optional] - NOT SUPPORTED
    private final static int DTNORMALIZE = 0x1 << 4;    
    
    // Parameter element-content-whitespace, true [required] (default) - value - false [optional] NOT SUPPORTED
    private final static int ELEM_CONTENT_WHITESPACE = 0x1 << 5;
    
    // Parameter entities, true [required] (default)
    private final static int ENTITIES = 0x1 << 6;
    
    // Parameter infoset, true [required] (default), false has no effect --> True has no effect for the serializer
    private final static int INFOSET = 0x1 << 7;
    
    // Parameter namespaces, true [required] (default)
    private final static int NAMESPACES = 0x1 << 8;
    
    // Parameter namespace-declarations, true [required] (default)
    private final static int NAMESPACEDECLS = 0x1 << 9;
    
    // Parameter normalize-characters, true [optional] - NOT SUPPORTED
    private final static int NORMALIZECHARS = 0x1 << 10;
    
    // Parameter split-cdata-sections, true [required] (default)
    private final static int SPLITCDATA = 0x1 << 11;   
    
    // Parameter validate, true [optional] - NOT SUPPORTED
    private final static int VALIDATE = 0x1 << 12;   
    
    // Parameter validate-if-schema, true [optional] - NOT SUPPORTED
    private final static int SCHEMAVALIDATE = 0x1 << 13;
    
    // Parameter split-cdata-sections, true [required] (default)
    private final static int WELLFORMED = 0x1 << 14;   
    
    // Parameter discard-default-content, true [required] (default)
    // Not sure how this will be used in level 2 Documents
    private final static int DISCARDDEFAULT = 0x1 << 15;       
    
    // Parameter format-pretty-print, true [optional] 
    private final static int PRETTY_PRINT = 0x1 << 16;
    
    // Parameter ignore-unknown-character-denormalizations, true [required] (default)
    // We currently do not support XML 1.1 character normalization
    private final static int IGNORE_CHAR_DENORMALIZE = 0x1 << 17;
    
    // Parameter discard-default-content, true [required] (default)
    private final static int XMLDECL = 0x1 << 18;    
    // ************************************************************************
    
    // Recognized parameters for which atleast one value can be set
    private String fRecognizedParameters [] = {
            DOMConstants.DOM_CANONICAL_FORM,
            DOMConstants.DOM_CDATA_SECTIONS,
            DOMConstants.DOM_CHECK_CHAR_NORMALIZATION,
            DOMConstants.DOM_COMMENTS,
            DOMConstants.DOM_DATATYPE_NORMALIZATION,
            DOMConstants.DOM_ELEMENT_CONTENT_WHITESPACE,
            DOMConstants.DOM_ENTITIES,
            DOMConstants.DOM_INFOSET,
            DOMConstants.DOM_NAMESPACES,
            DOMConstants.DOM_NAMESPACE_DECLARATIONS,
            //DOMConstants.DOM_NORMALIZE_CHARACTERS,
            DOMConstants.DOM_SPLIT_CDATA,
            DOMConstants.DOM_VALIDATE,
            DOMConstants.DOM_VALIDATE_IF_SCHEMA,
            DOMConstants.DOM_WELLFORMED,
            DOMConstants.DOM_DISCARD_DEFAULT_CONTENT,
            DOMConstants.DOM_FORMAT_PRETTY_PRINT,
            DOMConstants.DOM_IGNORE_UNKNOWN_CHARACTER_DENORMALIZATIONS,
            DOMConstants.DOM_XMLDECL,
            DOMConstants.DOM_ERROR_HANDLER
    };
    
    
    /**
     * Constructor:  Creates a LSSerializerImpl object.  The underlying
     * XML 1.0 or XML 1.1 org.apache.xml.serializer.Serializer object is
     * created and initialized the first time any of the write methods are  
     * invoked to serialize the Node.  Subsequent write methods on the same
     * LSSerializerImpl object will use the previously created Serializer object.
     */
    public LSSerializerImpl () {
        // set default parameters
        fFeatures |= CDATA;
        fFeatures |= COMMENTS;
        fFeatures |= ELEM_CONTENT_WHITESPACE;
        fFeatures |= ENTITIES;
        fFeatures |= NAMESPACES;
        fFeatures |= NAMESPACEDECLS;
        fFeatures |= SPLITCDATA;
        fFeatures |= WELLFORMED;
        fFeatures |= DISCARDDEFAULT;
        fFeatures |= XMLDECL;
        
        // New OutputFormat properties
        fDOMConfigProperties = new Properties();
        
        // Initialize properties to be passed on the underlying serializer
        initializeSerializerProps();
        
        // Create the underlying serializer.
        Properties  configProps = OutputPropertiesFactory.getDefaultMethodProperties("xml");
        
        // change xml version from 1.0 to 1.1
        //configProps.setProperty("version", "1.1");
        
        // Get a serializer that seriailizes according the the properties,
        // which in this case is to xml
        fXMLSerializer = SerializerFactory.getSerializer(configProps);
        
        // Initialize Serializer
        fXMLSerializer.setOutputFormat(fDOMConfigProperties);
    }
    
    /**
     * Initializes the underlying serializer's configuration depending on the
     * default DOMConfiguration parameters. This method must be called before a
     * node is to be serialized.
     * 
     * @xsl.usage internal
     */
    public void initializeSerializerProps () {
        // canonical-form
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_CANONICAL_FORM, DOMConstants.DOM3_DEFAULT_FALSE);
        
        // cdata-sections
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_CDATA_SECTIONS, DOMConstants.DOM3_DEFAULT_TRUE);
        
        // "check-character-normalization"
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_CHECK_CHAR_NORMALIZATION,
                DOMConstants.DOM3_DEFAULT_FALSE);
        
        // comments
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_COMMENTS, DOMConstants.DOM3_DEFAULT_TRUE);
        
        // datatype-normalization
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_DATATYPE_NORMALIZATION,
                DOMConstants.DOM3_DEFAULT_FALSE);
        
        // element-content-whitespace
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_ELEMENT_CONTENT_WHITESPACE,
                DOMConstants.DOM3_DEFAULT_TRUE);
        
        // entities
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_ENTITIES, DOMConstants.DOM3_DEFAULT_TRUE);
        // preserve entities
        fDOMConfigProperties.setProperty(DOMConstants.S_XERCES_PROPERTIES_NS
                + DOMConstants.DOM_ENTITIES, DOMConstants.DOM3_DEFAULT_TRUE);

        // error-handler
        // Should we set our default ErrorHandler
        /*
         * if (fDOMConfig.getParameter(Constants.DOM_ERROR_HANDLER) != null) {
         * fDOMErrorHandler =
         * (DOMErrorHandler)fDOMConfig.getParameter(Constants.DOM_ERROR_HANDLER); }
         */
        
        // infoset
        if ((fFeatures & INFOSET) != 0) {
            fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                    + DOMConstants.DOM_NAMESPACES, DOMConstants.DOM3_DEFAULT_TRUE);
            fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                    + DOMConstants.DOM_NAMESPACE_DECLARATIONS,
                    DOMConstants.DOM3_DEFAULT_TRUE);
            fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                    + DOMConstants.DOM_COMMENTS, DOMConstants.DOM3_DEFAULT_TRUE);
            fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                    + DOMConstants.DOM_ELEMENT_CONTENT_WHITESPACE,
                    DOMConstants.DOM3_DEFAULT_TRUE);
            fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                    + DOMConstants.DOM_WELLFORMED, DOMConstants.DOM3_DEFAULT_TRUE);
            fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                    + DOMConstants.DOM_ENTITIES, DOMConstants.DOM3_DEFAULT_FALSE);
            // preserve entities
            fDOMConfigProperties.setProperty(DOMConstants.S_XERCES_PROPERTIES_NS
                    + DOMConstants.DOM_ENTITIES, DOMConstants.DOM3_DEFAULT_FALSE);
            fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                    + DOMConstants.DOM_CDATA_SECTIONS,
                    DOMConstants.DOM3_DEFAULT_FALSE);
            fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                    + DOMConstants.DOM_VALIDATE_IF_SCHEMA,
                    DOMConstants.DOM3_DEFAULT_FALSE);
            fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                    + DOMConstants.DOM_DATATYPE_NORMALIZATION,
                    DOMConstants.DOM3_DEFAULT_FALSE);
        }
        
        // namespaces
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_NAMESPACES, DOMConstants.DOM3_DEFAULT_TRUE);
        
        // namespace-declarations
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_NAMESPACE_DECLARATIONS,
                DOMConstants.DOM3_DEFAULT_TRUE);
        
        // normalize-characters
        /*
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_NORMALIZE_CHARACTERS,
                DOMConstants.DOM3_DEFAULT_FALSE);
        */
        
        // split-cdata-sections
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_SPLIT_CDATA, DOMConstants.DOM3_DEFAULT_TRUE);
        
        // validate
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_VALIDATE, DOMConstants.DOM3_DEFAULT_FALSE);
        
        // validate-if-schema
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_VALIDATE_IF_SCHEMA,
                DOMConstants.DOM3_DEFAULT_FALSE);
        
        // well-formed
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_WELLFORMED, DOMConstants.DOM3_DEFAULT_TRUE);
        
        // pretty-print
        fDOMConfigProperties.setProperty(
                DOMConstants.S_XSL_OUTPUT_INDENT,
                DOMConstants.DOM3_DEFAULT_TRUE);
        fDOMConfigProperties.setProperty(
                OutputPropertiesFactory.S_KEY_INDENT_AMOUNT, Integer.toString(3));
        
        // 
        
        // discard-default-content
        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS
                + DOMConstants.DOM_DISCARD_DEFAULT_CONTENT,
                DOMConstants.DOM3_DEFAULT_TRUE);
        
        // xml-declaration
        fDOMConfigProperties.setProperty(DOMConstants.S_XSL_OUTPUT_OMIT_XML_DECL, "no");
        
    }    
    
    // ************************************************************************
    // DOMConfiguraiton implementation
    // ************************************************************************
    
    /** 
     * Checks if setting a parameter to a specific value is supported.    
     *  
     * @see org.w3c.dom.DOMConfiguration#canSetParameter(java.lang.String, java.lang.Object)
     * @since DOM Level 3
     * @param name A String containing the DOMConfiguration parameter name.
     * @param value An Object specifying the value of the corresponding parameter. 
     */
    public boolean canSetParameter(String name, Object value) {
        if (value instanceof Boolean){
            if ( name.equalsIgnoreCase(DOMConstants.DOM_CDATA_SECTIONS)
                    || name.equalsIgnoreCase(DOMConstants.DOM_COMMENTS)
                    || name.equalsIgnoreCase(DOMConstants.DOM_ENTITIES)
                    || name.equalsIgnoreCase(DOMConstants.DOM_INFOSET)
                    || name.equalsIgnoreCase(DOMConstants.DOM_ELEMENT_CONTENT_WHITESPACE)
                    || name.equalsIgnoreCase(DOMConstants.DOM_NAMESPACES)    
                    || name.equalsIgnoreCase(DOMConstants.DOM_NAMESPACE_DECLARATIONS)
                    || name.equalsIgnoreCase(DOMConstants.DOM_SPLIT_CDATA)
                    || name.equalsIgnoreCase(DOMConstants.DOM_WELLFORMED)    
                    || name.equalsIgnoreCase(DOMConstants.DOM_DISCARD_DEFAULT_CONTENT)
                    || name.equalsIgnoreCase(DOMConstants.DOM_FORMAT_PRETTY_PRINT)                
                    || name.equalsIgnoreCase(DOMConstants.DOM_XMLDECL)){
                // both values supported
                return true;
            }
            else if (name.equalsIgnoreCase(DOMConstants.DOM_CANONICAL_FORM)
                    || name.equalsIgnoreCase(DOMConstants.DOM_CHECK_CHAR_NORMALIZATION)
                    || name.equalsIgnoreCase(DOMConstants.DOM_DATATYPE_NORMALIZATION)
                    || name.equalsIgnoreCase(DOMConstants.DOM_VALIDATE_IF_SCHEMA)
                    || name.equalsIgnoreCase(DOMConstants.DOM_VALIDATE)
                    // || name.equalsIgnoreCase(DOMConstants.DOM_NORMALIZE_CHARACTERS)
                    ) {
                // true is not supported
                return !((Boolean)value).booleanValue();
            }
            else if (name.equalsIgnoreCase(DOMConstants.DOM_IGNORE_UNKNOWN_CHARACTER_DENORMALIZATIONS)) {
                // false is not supported
                return ((Boolean)value).booleanValue();
            }
        }
        else if (name.equalsIgnoreCase(DOMConstants.DOM_ERROR_HANDLER) &&
                value == null || value instanceof DOMErrorHandler){
            return true;
        }
        return false;
    }
    /**
     * This method returns the value of a parameter if known.
     * 
     * @see org.w3c.dom.DOMConfiguration#getParameter(java.lang.String)
     * 
     * @param name A String containing the DOMConfiguration parameter name 
     *             whose value is to be returned.
     * @return Object The value of the parameter if known. 
     */
    public Object getParameter(String name) throws DOMException {
        if (name.equalsIgnoreCase(DOMConstants.DOM_COMMENTS)) {
            return ((fFeatures & COMMENTS) != 0) ? Boolean.TRUE : Boolean.FALSE;
        } else if (name.equalsIgnoreCase(DOMConstants.DOM_CDATA_SECTIONS)) {
            return ((fFeatures & CDATA) != 0) ? Boolean.TRUE : Boolean.FALSE;
        } else if (name.equalsIgnoreCase(DOMConstants.DOM_ENTITIES)) {
            return ((fFeatures & ENTITIES) != 0) ? Boolean.TRUE : Boolean.FALSE;
        } else if (name.equalsIgnoreCase(DOMConstants.DOM_NAMESPACES)) {
            return ((fFeatures & NAMESPACES) != 0) ? Boolean.TRUE : Boolean.FALSE;
        } else if (name.equalsIgnoreCase(DOMConstants.DOM_NAMESPACE_DECLARATIONS)) {
            return ((fFeatures & NAMESPACEDECLS) != 0) ? Boolean.TRUE : Boolean.FALSE;
        } else if (name.equalsIgnoreCase(DOMConstants.DOM_SPLIT_CDATA)) {
            return ((fFeatures & SPLITCDATA) != 0) ? Boolean.TRUE : Boolean.FALSE;
        } else if (name.equalsIgnoreCase(DOMConstants.DOM_WELLFORMED)) {
            return ((fFeatures & WELLFORMED) != 0) ? Boolean.TRUE : Boolean.FALSE;
        }  else if (name.equalsIgnoreCase(DOMConstants.DOM_DISCARD_DEFAULT_CONTENT)) {
            return ((fFeatures & DISCARDDEFAULT) != 0) ? Boolean.TRUE : Boolean.FALSE;
        } else if (name.equalsIgnoreCase(DOMConstants.DOM_FORMAT_PRETTY_PRINT)) {
            return ((fFeatures & PRETTY_PRINT) != 0) ? Boolean.TRUE : Boolean.FALSE;
        } else if (name.equalsIgnoreCase(DOMConstants.DOM_XMLDECL)) {
            return ((fFeatures & XMLDECL) != 0) ? Boolean.TRUE : Boolean.FALSE;
        } else if (name.equalsIgnoreCase(DOMConstants.DOM_ELEMENT_CONTENT_WHITESPACE)) {
            return ((fFeatures & ELEM_CONTENT_WHITESPACE) != 0) ? Boolean.TRUE : Boolean.FALSE;
        } else if (name.equalsIgnoreCase(DOMConstants.DOM_FORMAT_PRETTY_PRINT)) {
            return ((fFeatures & PRETTY_PRINT) != 0) ? Boolean.TRUE : Boolean.FALSE;
        } else if (name.equalsIgnoreCase(DOMConstants.DOM_IGNORE_UNKNOWN_CHARACTER_DENORMALIZATIONS)) {
            return Boolean.TRUE;
        } else if (name.equalsIgnoreCase(DOMConstants.DOM_CANONICAL_FORM)
                || name.equalsIgnoreCase(DOMConstants.DOM_CHECK_CHAR_NORMALIZATION)
                || name.equalsIgnoreCase(DOMConstants.DOM_DATATYPE_NORMALIZATION) 
                // || name.equalsIgnoreCase(DOMConstants.DOM_NORMALIZE_CHARACTERS)                
                || name.equalsIgnoreCase(DOMConstants.DOM_VALIDATE)
                || name.equalsIgnoreCase(DOMConstants.DOM_VALIDATE_IF_SCHEMA)) {
            return Boolean.FALSE;
        } else if (name.equalsIgnoreCase(DOMConstants.DOM_INFOSET)){
            if ((fFeatures & ENTITIES) == 0 &&
                    (fFeatures & CDATA) == 0 &&
                    (fFeatures & ELEM_CONTENT_WHITESPACE) != 0 &&
                    (fFeatures & NAMESPACES) != 0 &&
                    (fFeatures & NAMESPACEDECLS) != 0 &&
                    (fFeatures & WELLFORMED) != 0 &&
                    (fFeatures & COMMENTS) != 0) {
                return Boolean.TRUE;
            }                 
            return Boolean.FALSE;
        } else if (name.equalsIgnoreCase(DOMConstants.DOM_ERROR_HANDLER)) {
            return fDOMErrorHandler;
        } else if (
                name.equalsIgnoreCase(DOMConstants.DOM_SCHEMA_LOCATION)
                || name.equalsIgnoreCase(DOMConstants.DOM_SCHEMA_TYPE)) {
            return null;
        } else {
            // Here we have to add the Xalan specific DOM Message Formatter
            String msg = Utils.messages.createMessage(
                    MsgKey.ER_FEATURE_NOT_FOUND,
                    new Object[] { name });
            throw new DOMException(DOMException.NOT_FOUND_ERR, msg);
        }
    }
    
    /**
     * This method returns a of the parameters supported by this DOMConfiguration object 
     * and for which at least one value can be set by the application
     * 
     * @see org.w3c.dom.DOMConfiguration#getParameterNames()
     * 
     * @return DOMStringList A list of DOMConfiguration parameters recognized
     *                       by the serializer
     */
    public DOMStringList getParameterNames() {
        return new DOMStringListImpl(fRecognizedParameters);
    }
    
    /**
     * This method sets the value of the named parameter.
     *   
     * @see org.w3c.dom.DOMConfiguration#setParameter(java.lang.String, java.lang.Object)
     * 
     * @param name A String containing the DOMConfiguration parameter name.
     * @param value An Object contaiing the parameters value to set.
     */
    public void setParameter(String name, Object value) throws DOMException {
        // If the value is a boolean
        if (value instanceof Boolean) {
            boolean state = ((Boolean) value).booleanValue();
            
            if (name.equalsIgnoreCase(DOMConstants.DOM_COMMENTS)) {
                fFeatures = state ? fFeatures | COMMENTS : fFeatures
                        & ~COMMENTS;
                // comments
                if (state) {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_COMMENTS, DOMConstants.DOM3_EXPLICIT_TRUE);
                } else {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_COMMENTS, DOMConstants.DOM3_EXPLICIT_FALSE);
                }                
            } else if (name.equalsIgnoreCase(DOMConstants.DOM_CDATA_SECTIONS)) {
                fFeatures =  state ? fFeatures | CDATA : fFeatures
                        & ~CDATA;
                // cdata-sections
                if (state) {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_CDATA_SECTIONS, DOMConstants.DOM3_EXPLICIT_TRUE);
                } else {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_CDATA_SECTIONS, DOMConstants.DOM3_EXPLICIT_FALSE);
                }
            } else if (name.equalsIgnoreCase(DOMConstants.DOM_ENTITIES)) {
                fFeatures = state ? fFeatures | ENTITIES : fFeatures
                        & ~ENTITIES;
                // entities
                if (state) {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_ENTITIES, DOMConstants.DOM3_EXPLICIT_TRUE);
                    fDOMConfigProperties.setProperty(
                            DOMConstants.S_XERCES_PROPERTIES_NS
                            + DOMConstants.DOM_ENTITIES, DOMConstants.DOM3_EXPLICIT_TRUE);
                } else {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_ENTITIES, DOMConstants.DOM3_EXPLICIT_FALSE);
                    fDOMConfigProperties.setProperty(
                            DOMConstants.S_XERCES_PROPERTIES_NS
                            + DOMConstants.DOM_ENTITIES, DOMConstants.DOM3_EXPLICIT_FALSE);
                }
            } else if (name.equalsIgnoreCase(DOMConstants.DOM_NAMESPACES)) {
                fFeatures = state ? fFeatures | NAMESPACES : fFeatures
                        & ~NAMESPACES;
                // namespaces
                if (state) {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_NAMESPACES, DOMConstants.DOM3_EXPLICIT_TRUE);
                } else {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_NAMESPACES, DOMConstants.DOM3_EXPLICIT_FALSE); 
                }       
            } else if (name
                    .equalsIgnoreCase(DOMConstants.DOM_NAMESPACE_DECLARATIONS)) {
                fFeatures = state ? fFeatures | NAMESPACEDECLS
                        : fFeatures & ~NAMESPACEDECLS;
                // namespace-declarations
                if (state) {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_NAMESPACE_DECLARATIONS, DOMConstants.DOM3_EXPLICIT_TRUE);
                } else {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_NAMESPACE_DECLARATIONS, DOMConstants.DOM3_EXPLICIT_FALSE); 
                } 
            } else if (name.equalsIgnoreCase(DOMConstants.DOM_SPLIT_CDATA)) {
                fFeatures = state ? fFeatures | SPLITCDATA : fFeatures
                        & ~SPLITCDATA;
                // split-cdata-sections
                if (state) {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_SPLIT_CDATA, DOMConstants.DOM3_EXPLICIT_TRUE);
                } else {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_SPLIT_CDATA, DOMConstants.DOM3_EXPLICIT_FALSE); 
                }  
            } else if (name.equalsIgnoreCase(DOMConstants.DOM_WELLFORMED)) {
                fFeatures = state ? fFeatures | WELLFORMED : fFeatures
                        & ~WELLFORMED;
                // well-formed
                if (state) {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_WELLFORMED, DOMConstants.DOM3_EXPLICIT_TRUE);
                } else {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_WELLFORMED, DOMConstants.DOM3_EXPLICIT_FALSE); 
                }                  
            } else if (name
                    .equalsIgnoreCase(DOMConstants.DOM_DISCARD_DEFAULT_CONTENT)) {
                fFeatures = state ? fFeatures | DISCARDDEFAULT
                        : fFeatures & ~DISCARDDEFAULT;
                // discard-default-content
                if (state) {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_DISCARD_DEFAULT_CONTENT, DOMConstants.DOM3_EXPLICIT_TRUE);
                } else {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_DISCARD_DEFAULT_CONTENT, DOMConstants.DOM3_EXPLICIT_FALSE); 
                }                    
            } else if (name.equalsIgnoreCase(DOMConstants.DOM_FORMAT_PRETTY_PRINT)) {
                fFeatures = state ? fFeatures | PRETTY_PRINT : fFeatures
                        & ~PRETTY_PRINT;
                // format-pretty-print
                if (state) {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_FORMAT_PRETTY_PRINT, DOMConstants.DOM3_EXPLICIT_TRUE);
                }
                else {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_FORMAT_PRETTY_PRINT, DOMConstants.DOM3_EXPLICIT_FALSE);
                }
            } else if (name.equalsIgnoreCase(DOMConstants.DOM_XMLDECL)) {
                fFeatures = state ? fFeatures | XMLDECL : fFeatures
                        & ~XMLDECL;
                if (state) {
                    fDOMConfigProperties.setProperty(DOMConstants.S_XSL_OUTPUT_OMIT_XML_DECL, "no");
                } else {
                    fDOMConfigProperties.setProperty(DOMConstants.S_XSL_OUTPUT_OMIT_XML_DECL, "yes"); 
                }       
            } else if (name.equalsIgnoreCase(DOMConstants.DOM_ELEMENT_CONTENT_WHITESPACE)) {
                fFeatures = state ? fFeatures | ELEM_CONTENT_WHITESPACE : fFeatures
                        & ~ELEM_CONTENT_WHITESPACE;
                // element-content-whitespace
                if (state) {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_ELEMENT_CONTENT_WHITESPACE, DOMConstants.DOM3_EXPLICIT_TRUE);
                } else {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_ELEMENT_CONTENT_WHITESPACE, DOMConstants.DOM3_EXPLICIT_FALSE);
                }            
            } else if (name.equalsIgnoreCase(DOMConstants.DOM_IGNORE_UNKNOWN_CHARACTER_DENORMALIZATIONS)) {
                // false is not supported
                if (!state) {
                    // Here we have to add the Xalan specific DOM Message Formatter
                    String msg = Utils.messages.createMessage(
                            MsgKey.ER_FEATURE_NOT_SUPPORTED,
                            new Object[] { name });
                    throw new DOMException(DOMException.NOT_SUPPORTED_ERR, msg);
                } else {
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_IGNORE_UNKNOWN_CHARACTER_DENORMALIZATIONS, DOMConstants.DOM3_EXPLICIT_TRUE);
                }
            } else if (name.equalsIgnoreCase(DOMConstants.DOM_CANONICAL_FORM)
                    || name.equalsIgnoreCase(DOMConstants.DOM_VALIDATE_IF_SCHEMA)
                    || name.equalsIgnoreCase(DOMConstants.DOM_VALIDATE)
                    || name.equalsIgnoreCase(DOMConstants.DOM_CHECK_CHAR_NORMALIZATION)
                    || name.equalsIgnoreCase(DOMConstants.DOM_DATATYPE_NORMALIZATION)
                    // || name.equalsIgnoreCase(DOMConstants.DOM_NORMALIZE_CHARACTERS)
                    ) {
                // true is not supported
                if (state) {
                    String msg = Utils.messages.createMessage(
                            MsgKey.ER_FEATURE_NOT_SUPPORTED,
                            new Object[] { name });
                    throw new DOMException(DOMException.NOT_SUPPORTED_ERR, msg);
                } else {
                    if (name.equalsIgnoreCase(DOMConstants.DOM_CANONICAL_FORM)) {
                        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                                + DOMConstants.DOM_CANONICAL_FORM, DOMConstants.DOM3_EXPLICIT_FALSE);
                    } else if (name.equalsIgnoreCase(DOMConstants.DOM_VALIDATE_IF_SCHEMA)) {
                        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                                + DOMConstants.DOM_VALIDATE_IF_SCHEMA, DOMConstants.DOM3_EXPLICIT_FALSE);
                    } else if (name.equalsIgnoreCase(DOMConstants.DOM_VALIDATE)) {
                        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                                + DOMConstants.DOM_VALIDATE, DOMConstants.DOM3_EXPLICIT_FALSE);
                    } else if (name.equalsIgnoreCase(DOMConstants.DOM_VALIDATE_IF_SCHEMA)) {
                        fDOMConfigProperties.setProperty(DOMConstants.DOM_CHECK_CHAR_NORMALIZATION 
                                + DOMConstants.DOM_CHECK_CHAR_NORMALIZATION, DOMConstants.DOM3_EXPLICIT_FALSE);
                    } else if (name.equalsIgnoreCase(DOMConstants.DOM_DATATYPE_NORMALIZATION)) {
                        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                                + DOMConstants.DOM_DATATYPE_NORMALIZATION, DOMConstants.DOM3_EXPLICIT_FALSE);
                    } /* else if (name.equalsIgnoreCase(DOMConstants.DOM_NORMALIZE_CHARACTERS)) {
                        fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                                + DOMConstants.DOM_NORMALIZE_CHARACTERS, DOMConstants.DOM3_EXPLICIT_FALSE);
                    } */
                }
            } else if (name.equalsIgnoreCase(DOMConstants.DOM_INFOSET)) {
                // infoset
                if (state) {
                    fFeatures &= ~ENTITIES;
                    fFeatures &= ~CDATA;
                    fFeatures &= ~SCHEMAVALIDATE;
                    fFeatures &= ~DTNORMALIZE;
                    fFeatures |= NAMESPACES;
                    fFeatures |= NAMESPACEDECLS;
                    fFeatures |= WELLFORMED;
                    fFeatures |= ELEM_CONTENT_WHITESPACE;
                    fFeatures |= COMMENTS;
                    
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_NAMESPACES, DOMConstants.DOM3_EXPLICIT_TRUE); 
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_NAMESPACE_DECLARATIONS, DOMConstants.DOM3_EXPLICIT_TRUE);
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_COMMENTS, DOMConstants.DOM3_EXPLICIT_TRUE);
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_ELEMENT_CONTENT_WHITESPACE, DOMConstants.DOM3_EXPLICIT_TRUE);
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_WELLFORMED, DOMConstants.DOM3_EXPLICIT_TRUE);
                    
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_ENTITIES, DOMConstants.DOM3_EXPLICIT_FALSE);
                    fDOMConfigProperties.setProperty(DOMConstants.S_XERCES_PROPERTIES_NS
                            + DOMConstants.DOM_ENTITIES, DOMConstants.DOM3_EXPLICIT_FALSE);
                    
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_CDATA_SECTIONS, DOMConstants.DOM3_EXPLICIT_FALSE);
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_VALIDATE_IF_SCHEMA, DOMConstants.DOM3_EXPLICIT_FALSE);            
                    fDOMConfigProperties.setProperty(DOMConstants.S_DOM3_PROPERTIES_NS 
                            + DOMConstants.DOM_DATATYPE_NORMALIZATION, DOMConstants.DOM3_EXPLICIT_FALSE);
                }
            } else {
                // If this is a non-boolean parameter a type mismatch should be thrown.
                if (name.equalsIgnoreCase(DOMConstants.DOM_ERROR_HANDLER) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_SCHEMA_LOCATION) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_SCHEMA_TYPE)) {
                    String msg = Utils.messages.createMessage(
                            MsgKey.ER_TYPE_MISMATCH_ERR,
                            new Object[] { name });
                    throw new DOMException(DOMException.TYPE_MISMATCH_ERR, msg);
                }
                
                // Parameter is not recognized
                String msg = Utils.messages.createMessage(
                        MsgKey.ER_FEATURE_NOT_FOUND,
                        new Object[] { name });
                throw new DOMException(DOMException.NOT_FOUND_ERR, msg);
            }
        } // If the parameter value is not a boolean 
        else if (name.equalsIgnoreCase(DOMConstants.DOM_ERROR_HANDLER)) {
            if (value == null || value instanceof DOMErrorHandler) {
                fDOMErrorHandler = (DOMErrorHandler)value;
            } else {
                String msg = Utils.messages.createMessage(
                        MsgKey.ER_TYPE_MISMATCH_ERR,
                        new Object[] { name });
                throw new DOMException(DOMException.TYPE_MISMATCH_ERR, msg);
            }
        } else if (
                name.equalsIgnoreCase(DOMConstants.DOM_SCHEMA_LOCATION)
                || name.equalsIgnoreCase(DOMConstants.DOM_SCHEMA_TYPE)) {
            if (value != null) {
                if (!(value instanceof String)) {
                    String msg = Utils.messages.createMessage(
                            MsgKey.ER_TYPE_MISMATCH_ERR,
                            new Object[] { name });
                    throw new DOMException(DOMException.TYPE_MISMATCH_ERR, msg);
                }
                String msg = Utils.messages.createMessage(
                        MsgKey.ER_FEATURE_NOT_SUPPORTED,
                        new Object[] { name });
                throw new DOMException(DOMException.NOT_SUPPORTED_ERR, msg);
            }
        } else {
            // If this is a boolean parameter a type mismatch should be thrown.
            if (name.equalsIgnoreCase(DOMConstants.DOM_COMMENTS) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_CDATA_SECTIONS) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_ENTITIES) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_NAMESPACES) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_NAMESPACE_DECLARATIONS) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_SPLIT_CDATA) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_WELLFORMED) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_DISCARD_DEFAULT_CONTENT) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_FORMAT_PRETTY_PRINT) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_XMLDECL) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_ELEMENT_CONTENT_WHITESPACE) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_IGNORE_UNKNOWN_CHARACTER_DENORMALIZATIONS) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_CANONICAL_FORM) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_VALIDATE_IF_SCHEMA) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_VALIDATE) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_CHECK_CHAR_NORMALIZATION) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_DATATYPE_NORMALIZATION) ||
                    name.equalsIgnoreCase(DOMConstants.DOM_INFOSET)) {
                String msg = Utils.messages.createMessage(
                        MsgKey.ER_TYPE_MISMATCH_ERR,
                        new Object[] { name });
                throw new DOMException(DOMException.TYPE_MISMATCH_ERR, msg);
            }
            
            // Parameter is not recognized
            String msg = Utils.messages.createMessage(
                    MsgKey.ER_FEATURE_NOT_FOUND,
                    new Object[] { name });
            throw new DOMException(DOMException.NOT_FOUND_ERR, msg);
        }
    }
    // ************************************************************************
    
    
    // ************************************************************************
    // DOMConfiguraiton implementation
    // ************************************************************************
    
    /** 
     * Returns the DOMConfiguration of the LSSerializer.
     *  
     * @see org.w3c.dom.ls.LSSerializer#getDomConfig()
     * @since DOM Level 3
     * @return A DOMConfiguration object.
     */
    public DOMConfiguration getDomConfig() {
        return (DOMConfiguration)this;
    }
    
    /** 
     * Returns the DOMConfiguration of the LSSerializer.
     *  
     * @see org.w3c.dom.ls.LSSerializer#getFilter()
     * @since DOM Level 3
     * @return A LSSerializerFilter object.
     */
    public LSSerializerFilter getFilter() {
        return fSerializerFilter;
    }
    
    /** 
     * Returns the End-Of-Line sequence of characters to be used in the XML 
     * being serialized.  If none is set a default "\n" is returned.
     * 
     * @see org.w3c.dom.ls.LSSerializer#getNewLine()
     * @since DOM Level 3
     * @return A String containing the end-of-line character sequence  used in 
     * serialization.
     */
    public String getNewLine() {
        return fEndOfLine;
    }
    
    /** 
     * Set a LSSerilizerFilter on the LSSerializer.  When set, the filter is
     * called before each node is serialized which depending on its implemention
     * determines if the node is to be serialized or not.    
     *  
     * @see org.w3c.dom.ls.LSSerializer#setFilter
     * @since DOM Level 3
     * @param filter A LSSerializerFilter to be applied to the stream to serialize.
     */
    public void setFilter(LSSerializerFilter filter) {
        fSerializerFilter = filter;
    }
    
    /** 
     * Sets the End-Of-Line sequence of characters to be used in the XML 
     * being serialized.  Setting this attribute to null will reset its 
     * value to the default value i.e. "\n".
     * 
     * @see org.w3c.dom.ls.LSSerializer#setNewLine
     * @since DOM Level 3
     * @param newLine a String that is the end-of-line character sequence to be used in 
     * serialization.
     */
    public void setNewLine(String newLine) {
        fEndOfLine = (newLine != null) ? newLine : DEFAULT_END_OF_LINE;
    }
    
    /** 
     * Serializes the specified node to the specified LSOutput and returns true if the Node 
     * was successfully serialized. 
     * 
     * @see org.w3c.dom.ls.LSSerializer#write(org.w3c.dom.Node, org.w3c.dom.ls.LSOutput)
     * @since DOM Level 3
     * @param nodeArg The Node to serialize.
     * @throws org.w3c.dom.ls.LSException SERIALIZE_ERR: Raised if the 
     * LSSerializer was unable to serialize the node.
     *      
     */
    public boolean write(Node nodeArg, LSOutput destination) throws LSException {
        // If the destination is null
        if (destination == null) {
            String msg = Utils.messages
            .createMessage(
                    MsgKey.ER_NO_OUTPUT_SPECIFIED,
                    null);
            if (fDOMErrorHandler != null) {
                fDOMErrorHandler.handleError(new DOMErrorImpl(
                        DOMError.SEVERITY_FATAL_ERROR, msg,
                        MsgKey.ER_NO_OUTPUT_SPECIFIED));
            }
            throw new LSException(LSException.SERIALIZE_ERR, msg);
        } 
        
        // If nodeArg is null, return false.  Should we throw and LSException instead?
        if (nodeArg == null ) {
            return false;
        }

        // Obtain a reference to the serializer to use
        // Serializer serializer = getXMLSerializer(xmlVersion);
        Serializer serializer = fXMLSerializer;
        serializer.reset();
        
        // If the node has not been seen
        if ( nodeArg != fVisitedNode) {
            // Determine the XML Document version of the Node 
            String xmlVersion = getXMLVersion(nodeArg);
            
            // Determine the encoding: 1.LSOutput.encoding, 2.Document.inputEncoding, 3.Document.xmlEncoding. 
            fEncoding = destination.getEncoding();
            if (fEncoding == null ) {
            	fEncoding = getInputEncoding(nodeArg);
            	fEncoding = fEncoding != null ? fEncoding : getXMLEncoding(nodeArg) == null? "UTF-8": getXMLEncoding(nodeArg);
            }

            // If the encoding is not recognized throw an exception.
            // Note: The serializer defaults to UTF-8 when created
            if (!Encodings.isRecognizedEncoding(fEncoding)) {
                String msg = Utils.messages
                .createMessage(
                        MsgKey.ER_UNSUPPORTED_ENCODING,
                        null);
                if (fDOMErrorHandler != null) {
                    fDOMErrorHandler.handleError(new DOMErrorImpl(
                            DOMError.SEVERITY_FATAL_ERROR, msg,
                            MsgKey.ER_UNSUPPORTED_ENCODING));
                }
                throw new LSException(LSException.SERIALIZE_ERR, msg);            	
            }
            
            serializer.getOutputFormat().setProperty("version", xmlVersion);

            // Set the output encoding and xml version properties
            fDOMConfigProperties.setProperty(DOMConstants.S_XERCES_PROPERTIES_NS + DOMConstants.S_XML_VERSION, xmlVersion);
            fDOMConfigProperties.setProperty(DOMConstants.S_XSL_OUTPUT_ENCODING, fEncoding);
            
            // If the node to be serialized is not a Document, Element, or Entity
            // node
            // then the XML declaration, or text declaration, should be never be
            // serialized.
            if ( (nodeArg.getNodeType() != Node.DOCUMENT_NODE
                    || nodeArg.getNodeType() != Node.ELEMENT_NODE
                    || nodeArg.getNodeType() != Node.ENTITY_NODE)
                    && ((fFeatures & XMLDECL) != 0)) {
                fDOMConfigProperties.setProperty(
                        DOMConstants.S_XSL_OUTPUT_OMIT_XML_DECL,
                        DOMConstants.DOM3_DEFAULT_FALSE);
            }

            fVisitedNode = nodeArg;
        } 
        
        // Update the serializer properties
        fXMLSerializer.setOutputFormat(fDOMConfigProperties);
        
        // 
        try {
            
            // The LSSerializer will use the LSOutput object to determine 
            // where to serialize the output to in the following order the  
            // first one that is not null and not an empty string will be    
            // used: 1.LSOutput.characterStream, 2.LSOutput.byteStream,   
            // 3. LSOutput.systemId 
            // 1.LSOutput.characterStream
            Writer writer = destination.getCharacterStream();
            if (writer == null ) {
                
                // 2.LSOutput.byteStream
                OutputStream outputStream = destination.getByteStream();
                if ( outputStream == null) {
                    
                    // 3. LSOutput.systemId
                    String uri = destination.getSystemId();
                    if (uri == null) {
                        String msg = Utils.messages
                        .createMessage(
                                MsgKey.ER_NO_OUTPUT_SPECIFIED,
                                null);
                        if (fDOMErrorHandler != null) {
                            fDOMErrorHandler.handleError(new DOMErrorImpl(
                                    DOMError.SEVERITY_FATAL_ERROR, msg,
                                    MsgKey.ER_NO_OUTPUT_SPECIFIED));
                        }
                        throw new LSException(LSException.SERIALIZE_ERR, msg);
                        
                    } else {
                        // Expand the System Id and obtain an absolute URI for it.
                        String absoluteURI = SystemIDResolver.getAbsoluteURI(uri);
                        
                        URL url = new URL(absoluteURI);
                        OutputStream urlOutStream = null;
                        String protocol = url.getProtocol();
                        String host = url.getHost();
                        
                        // For file protocols, there is no need to use a URL to get its
                        // corresponding OutputStream
                        
                        // Scheme names consist of a sequence of characters. The lower case
                        // letters "a"--"z", digits, and the characters plus ("+"), period
                        // ("."), and hyphen ("-") are allowed. For resiliency, programs
                        // interpreting URLs should treat upper case letters as equivalent to
                        // lower case in scheme names (e.g., allow "HTTP" as well as "http").
                        if (protocol.equalsIgnoreCase("file") 
                                && (host == null || host.length() == 0 || host.equals("localhost"))) {
                            // do we also need to check for host.equals(hostname)
                            urlOutStream = new FileOutputStream(getPathWithoutEscapes(url.getPath()));
                           
                        } else {
                            // This should support URL's whose schemes are mentioned in 
                            // RFC1738 other than file
                            
                            URLConnection urlCon = url.openConnection();
                            urlCon.setDoInput(false);
                            urlCon.setDoOutput(true);
                            urlCon.setUseCaches(false); 
                            urlCon.setAllowUserInteraction(false);
                            
                            // When writing to a HTTP URI, a HTTP PUT is performed.
                            if (urlCon instanceof HttpURLConnection) {
                                HttpURLConnection httpCon = (HttpURLConnection) urlCon;
                                httpCon.setRequestMethod("PUT");
                            }
                            urlOutStream = urlCon.getOutputStream();
                        }
                        // set the OutputStream to that obtained from the systemId
                        serializer.setOutputStream(urlOutStream);
                    }
                } else {
                    // 2.LSOutput.byteStream
                    serializer.setOutputStream(outputStream);     
                }
            } else {
                // 1.LSOutput.characterStream
                serializer.setWriter(writer);
            }
            
            // The associated media type by default is set to text/xml on 
            // org.apache.xml.serializer.SerializerBase.  
            
            // Get a reference to the serializer then lets you serilize a DOM
            // Use this hack till Xalan support JAXP1.3
            if (fDOMSerializer == null) {
               fDOMSerializer = (DOM3Serializer)serializer.asDOM3Serializer();
            } 
            
            // Set the error handler on the DOM3Serializer interface implementation
            if (fDOMErrorHandler != null) {
                fDOMSerializer.setErrorHandler(fDOMErrorHandler);
            }
            
            // Set the filter on the DOM3Serializer interface implementation
            if (fSerializerFilter != null) {
                fDOMSerializer.setNodeFilter(fSerializerFilter);
            }
            
            // Set the NewLine character to be used
            fDOMSerializer.setNewLine(fEndOfLine.toCharArray());
            
            // Serializer your DOM, where node is an org.w3c.dom.Node
            // Assuming that Xalan's serializer can serialize any type of DOM node
            fDOMSerializer.serializeDOM3(nodeArg);
            
        } catch( UnsupportedEncodingException ue) {
            
            String msg = Utils.messages
            .createMessage(
                    MsgKey.ER_UNSUPPORTED_ENCODING,
                    null);
            if (fDOMErrorHandler != null) {
                fDOMErrorHandler.handleError(new DOMErrorImpl(
                        DOMError.SEVERITY_FATAL_ERROR, msg,
                        MsgKey.ER_UNSUPPORTED_ENCODING, ue));
            }
            throw (LSException) createLSException(LSException.SERIALIZE_ERR, ue).fillInStackTrace();
        } catch (LSException lse) {
            // Rethrow LSException.
            throw lse;
        } catch (RuntimeException e) {
            throw (LSException) createLSException(LSException.SERIALIZE_ERR, e).fillInStackTrace();
        }  catch (Exception e) {
            if (fDOMErrorHandler != null) {
                fDOMErrorHandler.handleError(new DOMErrorImpl(
                        DOMError.SEVERITY_FATAL_ERROR, e.getMessage(),
                        null, e));
            }
            throw (LSException) createLSException(LSException.SERIALIZE_ERR, e).fillInStackTrace();
        }        
        return true;
    }
    
    /** 
     * Serializes the specified node and returns a String with the serialized
     * data to the caller.  
     * 
     * @see org.w3c.dom.ls.LSSerializer#writeToString(org.w3c.dom.Node)
     * @since DOM Level 3
     * @param nodeArg The Node to serialize.
     * @throws org.w3c.dom.ls.LSException SERIALIZE_ERR: Raised if the 
     * LSSerializer was unable to serialize the node.
     *      
     */
    public String writeToString(Node nodeArg) throws DOMException, LSException {
        // return null is nodeArg is null.  Should an Exception be thrown instead?
        if (nodeArg == null) {
            return null;
        }

        // Should we reset the serializer configuration before each write operation?
        // Obtain a reference to the serializer to use
        Serializer serializer = fXMLSerializer;
        serializer.reset();
        
        if (nodeArg != fVisitedNode){
            // Determine the XML Document version of the Node 
            String xmlVersion = getXMLVersion(nodeArg);
            
            serializer.getOutputFormat().setProperty("version", xmlVersion);
            
            // Set the output encoding and xml version properties
            fDOMConfigProperties.setProperty(DOMConstants.S_XERCES_PROPERTIES_NS + DOMConstants.S_XML_VERSION, xmlVersion);
            fDOMConfigProperties.setProperty(DOMConstants.S_XSL_OUTPUT_ENCODING, "UTF-16");
            
            // If the node to be serialized is not a Document, Element, or Entity
            // node
            // then the XML declaration, or text declaration, should be never be
            // serialized.
            if  ((nodeArg.getNodeType() != Node.DOCUMENT_NODE
                    || nodeArg.getNodeType() != Node.ELEMENT_NODE
                    || nodeArg.getNodeType() != Node.ENTITY_NODE)
                    && ((fFeatures & XMLDECL) != 0)) {
                fDOMConfigProperties.setProperty(
                        DOMConstants.S_XSL_OUTPUT_OMIT_XML_DECL,
                        DOMConstants.DOM3_DEFAULT_FALSE);
            }            

            fVisitedNode = nodeArg;       
        } 
        // Update the serializer properties
        fXMLSerializer.setOutputFormat(fDOMConfigProperties);
        
        // StringWriter to Output to
        StringWriter output = new StringWriter();
        
        // 
        try {
            
            // Set the Serializer's Writer to a StringWriter
            serializer.setWriter(output);
            
            // Get a reference to the serializer then lets you serilize a DOM
            // Use this hack till Xalan support JAXP1.3
            if (fDOMSerializer == null) {
                fDOMSerializer = (DOM3Serializer)serializer.asDOM3Serializer();
            } 
                        
            // Set the error handler on the DOM3Serializer interface implementation
            if (fDOMErrorHandler != null) {
                fDOMSerializer.setErrorHandler(fDOMErrorHandler);
            }
            
            // Set the filter on the DOM3Serializer interface implementation
            if (fSerializerFilter != null) {
                fDOMSerializer.setNodeFilter(fSerializerFilter);
            }
            
            // Set the NewLine character to be used
            fDOMSerializer.setNewLine(fEndOfLine.toCharArray());
            
            // Serializer your DOM, where node is an org.w3c.dom.Node
            fDOMSerializer.serializeDOM3(nodeArg);
        } catch (LSException lse) {
            // Rethrow LSException.
            throw lse;
        } catch (RuntimeException e) {
            throw (LSException) createLSException(LSException.SERIALIZE_ERR, e).fillInStackTrace();
        }  catch (Exception e) {
            if (fDOMErrorHandler != null) {
                fDOMErrorHandler.handleError(new DOMErrorImpl(
                        DOMError.SEVERITY_FATAL_ERROR, e.getMessage(),
                        null, e));
            }
            throw (LSException) createLSException(LSException.SERIALIZE_ERR, e).fillInStackTrace();
        }        
        
        // return the serialized string
        return output.toString();
    }
    
    /** 
     * Serializes the specified node to the specified URI and returns true if the Node 
     * was successfully serialized. 
     * 
     * @see org.w3c.dom.ls.LSSerializer#writeToURI(org.w3c.dom.Node, String)
     * @since DOM Level 3
     * @param nodeArg The Node to serialize.
     * @throws org.w3c.dom.ls.LSException SERIALIZE_ERR: Raised if the 
     * LSSerializer was unable to serialize the node.
     *      
     */
    public boolean writeToURI(Node nodeArg, String uri) throws LSException {
        // If nodeArg is null, return false.  Should we throw and LSException instead?
        if (nodeArg == null ) {
            return false;
        }

        // Obtain a reference to the serializer to use
        Serializer serializer = fXMLSerializer;
        serializer.reset();
        
        if (nodeArg != fVisitedNode) {
            // Determine the XML Document version of the Node 
            String xmlVersion = getXMLVersion(nodeArg);
            
            // Determine the encoding: 1.LSOutput.encoding,
            // 2.Document.inputEncoding, 3.Document.xmlEncoding.
            fEncoding = getInputEncoding(nodeArg);
            if (fEncoding == null ) {
            	fEncoding = fEncoding != null ? fEncoding : getXMLEncoding(nodeArg) == null? "UTF-8": getXMLEncoding(nodeArg);
            }
            
            serializer.getOutputFormat().setProperty("version", xmlVersion);
            
            // Set the output encoding and xml version properties
            fDOMConfigProperties.setProperty(DOMConstants.S_XERCES_PROPERTIES_NS + DOMConstants.S_XML_VERSION, xmlVersion);
            fDOMConfigProperties.setProperty(DOMConstants.S_XSL_OUTPUT_ENCODING, fEncoding);
            
            // If the node to be serialized is not a Document, Element, or Entity
            // node
            // then the XML declaration, or text declaration, should be never be
            // serialized.
            if ( (nodeArg.getNodeType() != Node.DOCUMENT_NODE
                    || nodeArg.getNodeType() != Node.ELEMENT_NODE
                    || nodeArg.getNodeType() != Node.ENTITY_NODE)
                    && ((fFeatures & XMLDECL) != 0))  {
                fDOMConfigProperties.setProperty(
                        DOMConstants.S_XSL_OUTPUT_OMIT_XML_DECL,
                        DOMConstants.DOM3_DEFAULT_FALSE);
            }
       
            fVisitedNode = nodeArg;
        } 
        
        // Update the serializer properties
        fXMLSerializer.setOutputFormat(fDOMConfigProperties);
        
        // 
        try {
            // If the specified encoding is not supported an
            // "unsupported-encoding" fatal error is raised. ??
            if (uri == null) {
                String msg = Utils.messages.createMessage(
                        MsgKey.ER_NO_OUTPUT_SPECIFIED, null);
                if (fDOMErrorHandler != null) {
                    fDOMErrorHandler.handleError(new DOMErrorImpl(
                            DOMError.SEVERITY_FATAL_ERROR, msg,
                            MsgKey.ER_NO_OUTPUT_SPECIFIED));
                }
                throw new LSException(LSException.SERIALIZE_ERR, msg);
                
            } else {
                // REVISIT: Can this be used to get an absolute expanded URI
                String absoluteURI = SystemIDResolver.getAbsoluteURI(uri);
                
                URL url = new URL(absoluteURI);
                OutputStream urlOutStream = null;
                String protocol = url.getProtocol();
                String host = url.getHost();
                
                // For file protocols, there is no need to use a URL to get its
                // corresponding OutputStream
                
                // Scheme names consist of a sequence of characters. The lower 
                // case letters "a"--"z", digits, and the characters plus ("+"), 
                // period ("."), and hyphen ("-") are allowed. For resiliency, 
                // programs interpreting URLs should treat upper case letters as
                // equivalent to lower case in scheme names 
                // (e.g., allow "HTTP" as well as "http").
                if (protocol.equalsIgnoreCase("file")
                        && (host == null || host.length() == 0 || host
                                .equals("localhost"))) {
                    // do we also need to check for host.equals(hostname)
                    urlOutStream = new FileOutputStream(getPathWithoutEscapes(url.getPath()));
                    
                } else {
                    // This should support URL's whose schemes are mentioned in
                    // RFC1738 other than file
                    
                    URLConnection urlCon = url.openConnection();
                    urlCon.setDoInput(false);
                    urlCon.setDoOutput(true);
                    urlCon.setUseCaches(false);
                    urlCon.setAllowUserInteraction(false);
                    
                    // When writing to a HTTP URI, a HTTP PUT is performed.
                    if (urlCon instanceof HttpURLConnection) {
                        HttpURLConnection httpCon = (HttpURLConnection) urlCon;
                        httpCon.setRequestMethod("PUT");
                    }
                    urlOutStream = urlCon.getOutputStream();
                }
                // set the OutputStream to that obtained from the systemId
                serializer.setOutputStream(urlOutStream);
            }
            
            // Get a reference to the serializer then lets you serilize a DOM
            // Use this hack till Xalan support JAXP1.3
            if (fDOMSerializer == null) {
                fDOMSerializer = (DOM3Serializer)serializer.asDOM3Serializer();
            } 
            
            // Set the error handler on the DOM3Serializer interface implementation
            if (fDOMErrorHandler != null) {
                fDOMSerializer.setErrorHandler(fDOMErrorHandler);
            }
            
            // Set the filter on the DOM3Serializer interface implementation
            if (fSerializerFilter != null) {
                fDOMSerializer.setNodeFilter(fSerializerFilter);
            }
            
            // Set the NewLine character to be used
            fDOMSerializer.setNewLine(fEndOfLine.toCharArray());
            
            // Serializer your DOM, where node is an org.w3c.dom.Node
            // Assuming that Xalan's serializer can serialize any type of DOM
            // node
            fDOMSerializer.serializeDOM3(nodeArg);
            
        } catch (LSException lse) {
            // Rethrow LSException.
            throw lse;
        } catch (RuntimeException e) {
            throw (LSException) createLSException(LSException.SERIALIZE_ERR, e).fillInStackTrace();
        }  catch (Exception e) {
            if (fDOMErrorHandler != null) {
                fDOMErrorHandler.handleError(new DOMErrorImpl(
                        DOMError.SEVERITY_FATAL_ERROR, e.getMessage(),
                        null, e));
            }
            throw (LSException) createLSException(LSException.SERIALIZE_ERR, e).fillInStackTrace();
        }        
        
        return true;
    }
    // ************************************************************************
    
    
    // ************************************************************************
    // Implementaion methods
    // ************************************************************************
    
    /** 
     * Determines the XML Version of the Document Node to serialize.  If the Document Node
     * is not a DOM Level 3 Node, then the default version returned is 1.0.
     * 
     * @param  nodeArg The Node to serialize
     * @return A String containing the version pseudo-attribute of the XMLDecl.  
     * @throws Throwable if the DOM implementation does not implement Document.getXmlVersion()      
     */
    //protected String getXMLVersion(Node nodeArg) throws Throwable {
    protected String getXMLVersion(Node nodeArg) {
        Document doc = null;
        
        // Determine the XML Version of the document
        if (nodeArg != null) {
            if (nodeArg.getNodeType() == Node.DOCUMENT_NODE) {
                // The Document node is the Node argument
                doc = (Document)nodeArg;
            } else { 
                // The Document node is the Node argument's ownerDocument
                doc = nodeArg.getOwnerDocument();
            }
            
            // Determine the DOM Version.
            if (doc != null && doc.getImplementation().hasFeature("Core","3.0")) {
                return doc.getXmlVersion();
            }
        } 
        // The version will be treated as "1.0" which may result in
        // an ill-formed document being serialized.
        // If nodeArg does not have an ownerDocument, treat this as XML 1.0
        return "1.0";
    }
    
    /** 
     * Determines the XML Encoding of the Document Node to serialize.  If the Document Node
     * is not a DOM Level 3 Node, then the default encoding "UTF-8" is returned.
     * 
     * @param  nodeArg The Node to serialize
     * @return A String containing the encoding pseudo-attribute of the XMLDecl.  
     * @throws Throwable if the DOM implementation does not implement Document.getXmlEncoding()     
     */
    protected String getXMLEncoding(Node nodeArg) {
        Document doc = null;
        
        // Determine the XML Encoding of the document
        if (nodeArg != null) {
            if (nodeArg.getNodeType() == Node.DOCUMENT_NODE) {
                // The Document node is the Node argument
                doc = (Document)nodeArg;
            } else { 
                // The Document node is the Node argument's ownerDocument
                doc = nodeArg.getOwnerDocument();
            }
            
            // Determine the XML Version. 
            if (doc != null && doc.getImplementation().hasFeature("Core","3.0")) {
                return doc.getXmlEncoding();
            }
        } 
        // The default encoding is UTF-8 except for the writeToString method
        return "UTF-8";
    }
    
    /** 
     * Determines the Input Encoding of the Document Node to serialize.  If the Document Node
     * is not a DOM Level 3 Node, then null is returned.
     * 
     * @param  nodeArg The Node to serialize
     * @return A String containing the input encoding.  
     */
    protected String getInputEncoding(Node nodeArg)  {
        Document doc = null;
        
        // Determine the Input Encoding of the document
        if (nodeArg != null) {
            if (nodeArg.getNodeType() == Node.DOCUMENT_NODE) {
                // The Document node is the Node argument
                doc = (Document)nodeArg;
            } else { 
                // The Document node is the Node argument's ownerDocument
                doc = nodeArg.getOwnerDocument();
            }
            
            // Determine the DOM Version.
            if (doc != null && doc.getImplementation().hasFeature("Core","3.0")) {
                return doc.getInputEncoding();
            }
        } 
        // The default encoding returned is null
        return null;
    }
    
    /**
     * This method returns the LSSerializer's error handler.
     * 
     * @return Returns the fDOMErrorHandler.
     */
    public DOMErrorHandler getErrorHandler() {
        return fDOMErrorHandler;
    }
    
    /**
     * Replaces all escape sequences in the given path with their literal characters.
     */
    private static String getPathWithoutEscapes(String origPath) {
        if (origPath != null && origPath.length() != 0 && origPath.indexOf('%') != -1) {
            // Locate the escape characters
            StringTokenizer tokenizer = new StringTokenizer(origPath, "%");
            StringBuffer result = new StringBuffer(origPath.length());
            int size = tokenizer.countTokens();
            result.append(tokenizer.nextToken());
            for(int i = 1; i < size; ++i) {
                String token = tokenizer.nextToken();
                if (token.length() >= 2 && isHexDigit(token.charAt(0)) && 
                        isHexDigit(token.charAt(1))) {
                    // Decode the 2 digit hexadecimal number following % in '%nn'
                    result.append((char)Integer.valueOf(token.substring(0, 2), 16).intValue());
                    token = token.substring(2);
                }
                result.append(token);
            }
            return result.toString();
        }
        return origPath;
    }

    /** 
     * Returns true if the given character is a valid hex character.
     */
    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9' || 
                c >= 'a' && c <= 'f' || 
                c >= 'A' && c <= 'F');
    }
    
    /**
     * Creates an LSException. On J2SE 1.4 and above the cause for the exception will be set.
     */
    private static LSException createLSException(short code, Throwable cause) {
        LSException lse = new LSException(code, cause != null ? cause.getMessage() : null);
        if (cause != null && ThrowableMethods.fgThrowableMethodsAvailable) {
            try {
                ThrowableMethods.fgThrowableInitCauseMethod.invoke(lse, new Object [] {cause});
            }
            // Something went wrong. There's not much we can do about it.
            catch (Exception e) {}
        }
        return lse;
    }
    
    /**
     * Holder of methods from java.lang.Throwable.
     */
    static class ThrowableMethods {
        
        // Method: java.lang.Throwable.initCause(java.lang.Throwable)
        private static java.lang.reflect.Method fgThrowableInitCauseMethod = null;
        
        // Flag indicating whether or not Throwable methods available.
        private static boolean fgThrowableMethodsAvailable = false;
        
        private ThrowableMethods() {}
        
        // Attempt to get methods for java.lang.Throwable on class initialization.
        static {
            try {
                fgThrowableInitCauseMethod = Throwable.class.getMethod("initCause", new Class [] {Throwable.class});
                fgThrowableMethodsAvailable = true;
            }
            // ClassNotFoundException, NoSuchMethodException or SecurityException
            // Whatever the case, we cannot use java.lang.Throwable.initCause(java.lang.Throwable).
            catch (Exception exc) {
                fgThrowableInitCauseMethod = null;
                fgThrowableMethodsAvailable = false;
            }
        }
    }
}
