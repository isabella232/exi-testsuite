package fc.fp.util.xas;

import java.util.Hashtable;

/**
 * A content decoder implementing chaining of decoders.  The typical
 * usage pattern for {@link ContentDecoder} implementations is to
 * chain them, i.e. a decoder is given a pre-existing decoder, which
 * it delegates to if it does not recognize the type to decode.  Since
 * this chaining also requires implementing the maintenance of the
 * prefix mappings, this abstract class implementing those methods and
 * containing the chain object is useful to avoid repeating code.
 */
public abstract class ChainedContentDecoder extends ContentDecoder {

    /**
     * The decoder to chain invocations to.  This should be
     * initialized in the subclass constructor if provided.
     */
    protected ContentDecoder chain = null;

    protected ChainedContentDecoder (Hashtable prefixMapping) {
	super(prefixMapping);
    }

    public void insertPrefixMapping (String namespace, String prefix) {
	//System.out.println(this.toString());
	//System.out.println("Mapping: " + namespace + "=" + prefix);
	if (chain != null) {
	    chain.insertPrefixMapping(namespace, prefix);
	} else {
	    super.insertPrefixMapping(namespace, prefix);
	}
    }

    public void deletePrefixMapping (String prefix) {
	if (chain != null) {
	    chain.deletePrefixMapping(prefix);
	} else {
	    super.deletePrefixMapping(prefix);
	}
    }

    public String mapNamespace (String prefix) {
	//System.out.println(this.toString());
	//System.out.println("Getting namespace for " + prefix);
	if (chain != null) {
	    return chain.mapNamespace(prefix);
	} else {
	    return super.mapNamespace(prefix);
	}
    }

    public void setRoot (ContentDecoder root) {
	super.setRoot(root);
	if (chain != null) {
	    chain.setRoot(root);
	}
    }

    public String toString () {
	return this.getClass().toString() + "(" + chain + ")";
    }

}
// arch-tag: 500b9288517d2ca1f2bfda8560161a61 *-
