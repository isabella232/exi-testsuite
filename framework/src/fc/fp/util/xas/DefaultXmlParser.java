package fc.fp.util.xas;

import org.xmlpull.v1.XmlPullParserException;

import org.kxml2.io.KXmlParser;

/**
 * Default parser implementation for XML.  This is an implementation
 * of the <code>XmlPullParser</code> interface that recognizes the
 * {@link XasUtil#PROPERTY_CONTENT_CODEC} property.  By default it
 * contains the {@link XmlSchemaContentDecoder} for typed content.
 *
 * <p>Note that normally the parser does not care about typed content,
 * but rather just constructs {@link Event#CONTENT} events.  However,
 * the parser is a logical place to put the typed content decoder, and
 * this placement is also symmetric with respect to how typed content
 * encoders are handled.
 */
public class DefaultXmlParser extends KXmlParser implements TypedXmlParser {

    private ContentDecoder decoder = new XmlSchemaContentDecoder();

    public void setProperty (String name, Object value)
	throws XmlPullParserException {
	if (XasUtil.PROPERTY_CONTENT_CODEC.equals(name)) {
	    if (value instanceof ContentDecoder) {
		decoder = (ContentDecoder) value;
	    } else {
		throw new IllegalArgumentException("Not a ContentDecoder: "
						   + value);
	    }
	} else {
	    super.setProperty(name, value);
	}
    }

    public Object getProperty (String name) {
	if (XasUtil.PROPERTY_CONTENT_CODEC.equals(name)) {
	    return decoder;
	} else {
	    return super.getProperty(name);
	}
    }

    public Object getObject () {
	return getText();
    }

}
// arch-tag: 3df06b34a9f4fce6ddb4b24520873673 *-
