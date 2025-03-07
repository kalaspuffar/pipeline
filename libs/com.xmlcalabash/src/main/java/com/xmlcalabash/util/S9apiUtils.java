/*
 * S9apiUtils.java
 *
 * Copyright 2008 Mark Logic Corporation.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.xmlcalabash.util;

import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.TreeReceiver;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.FingerprintedQName;
import net.sf.saxon.om.InscopeNamespaceResolver;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NameOfNode;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.Destination;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SAXDestination;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.NamespaceIterator;
import nu.validator.htmlparser.sax.HtmlSerializer;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

/**
 *
 * @author ndw
 */
public class S9apiUtils {
    private static final QName vara = new QName("","vara");
    private static final QName varb = new QName("","varb");

    /**
     * Write an XdmValue to a given destination. The sequence represented by the XdmValue is "normalized"
     * as defined in the serialization specification (this is equivalent to constructing a document node
     * in XSLT or XQuery with this sequence as the content expression), and the resulting document is
     * then copied to the destination. If the destination is a serializer this has the effect of serializing
     * the sequence as described in the W3C specifications.
     * @param runtime The runtime
     * @param values the value to be written
     * @param destination the destination to which the value is to be written
     * @param baseURI the base URI
     * @throws SaxonApiException if something goes wrong
     */

    public static void writeXdmValue(XProcRuntime runtime, Vector<XdmValue> values, Destination destination, URI baseURI) throws SaxonApiException {
        writeXdmValue(runtime.getProcessor(), values, destination, baseURI);
    }

    public static void writeXdmValue(Processor proc, Vector<XdmValue> values, Destination destination, URI baseURI) throws SaxonApiException {
        try {
            Configuration config = proc.getUnderlyingConfiguration();
            PipelineConfiguration pipeConfig = config.makePipelineConfiguration();

            Receiver out = destination.getReceiver(config);
            TreeReceiver tree = new TreeReceiver(out);
            tree.setPipelineConfiguration(pipeConfig);
            if (baseURI != null) {
                tree.setSystemId(baseURI.toASCIIString());
            }
            tree.open();
            tree.startDocument(0);
            for (XdmValue value : values) {
                Location locationId; {
                    if (baseURI != null && config.isLineNumbering()) {
                        int lineNumber = value instanceof XdmNode ? ((XdmNode)value).getLineNumber() : -1;
                        if (lineNumber > 0)
                            locationId = new LineNumberLocation(baseURI.toASCIIString(), lineNumber);
                        else
                            locationId = VoidLocation.instance();
                    } else
                        locationId = VoidLocation.instance();
                }
                for (XdmItem item : (Iterable<XdmItem>) value) {
                    tree.append((Item) item.getUnderlyingValue(), locationId, NodeInfo.ALL_NAMESPACES);
                }
            }
            tree.endDocument();
            tree.close();
        } catch (XPathException err) {
            throw new SaxonApiException(err);
        }
    }

    public static void writeXdmValue(XProcRuntime runtime, XdmItem node, Destination destination, URI baseURI) throws SaxonApiException {
        try {
            Processor proc = runtime.getProcessor();
            Configuration config = proc.getUnderlyingConfiguration();
            PipelineConfiguration pipeConfig = config.makePipelineConfiguration();

            Receiver out = destination.getReceiver(config);
            TreeReceiver tree = new TreeReceiver(out);
            tree.setPipelineConfiguration(pipeConfig);
            if (baseURI != null) {
                tree.setSystemId(baseURI.toASCIIString());
            }
            tree.open();
            tree.startDocument(0);
            Location locationId; {
                if (baseURI != null && config.isLineNumbering()) {
                    int lineNumber = node instanceof XdmNode ? ((XdmNode)node).getLineNumber() : -1;
                    if (lineNumber > 0)
                        locationId = new LineNumberLocation(baseURI.toASCIIString(), lineNumber);
                    else
                        locationId = VoidLocation.instance();
                } else
                    locationId = VoidLocation.instance();
            }
            tree.append((Item) node.getUnderlyingValue(), locationId, NodeInfo.ALL_NAMESPACES);
            tree.endDocument();
            tree.close();
        } catch (XPathException err) {
            throw new SaxonApiException(err);
        }
    }

    public static XdmNode getDocumentElement(XdmNode doc) {
        if (doc.getNodeKind() == XdmNodeKind.DOCUMENT) {
            for (XdmNode node : new AxisNodes(doc, Axis.CHILD, AxisNodes.SIGNIFICANT)) {
                if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
                    return node; // There can be only one, this is an XML document
                }
            }
            return null;
        } else {
            return doc;
        }
    }

    public static void serialize(XProcRuntime xproc, XdmNode node, Serializer serializer) throws SaxonApiException {
        Vector<XdmNode> nodes = new Vector<XdmNode> ();
        nodes.add(node);
        serialize(xproc, nodes, serializer);
    }

    public static void serialize(XProcRuntime xproc, Vector<XdmNode> nodes, Serializer serializer) throws SaxonApiException {
        Processor qtproc = xproc.getProcessor();
        XQueryCompiler xqcomp = qtproc.newXQueryCompiler();
        xqcomp.setModuleURIResolver(xproc.getResolver());

        // Patch suggested by oXygen to avoid errors that result from attempting to serialize
        // a schema-valid document with a schema-naive query
        xqcomp.getUnderlyingStaticContext().setSchemaAware(
                xqcomp.getProcessor().getUnderlyingConfiguration().isLicensedFeature(
                        Configuration.LicenseFeature.ENTERPRISE_XQUERY));

        XQueryExecutable xqexec = xqcomp.compile(".");
        XQueryEvaluator xqeval = xqexec.load();
        if (xproc.getHtmlSerializer() && "html".equals(serializer.getOutputProperty(Serializer.Property.METHOD))) {
            ContentHandler ch = null;
            Object outputDest = serializer.getOutputDestination();

            if (outputDest == null) {
                //???
                xqeval.setDestination(serializer);
            } else if (outputDest instanceof OutputStream) {
                ch = new HtmlSerializer((OutputStream) outputDest);
                xqeval.setDestination(new SAXDestination(ch));
            } else if (outputDest instanceof Writer) {
                ch = new HtmlSerializer((Writer) outputDest);
                xqeval.setDestination(new SAXDestination(ch));
            } else if (outputDest instanceof File) {
                try {
                    FileOutputStream fos = new FileOutputStream((File) outputDest);
                    ch = new HtmlSerializer(fos);
                    xqeval.setDestination(new SAXDestination(ch));
                } catch (FileNotFoundException fnfe) {
                    xqeval.setDestination(serializer);
                }
            } else {
                //???
                xqeval.setDestination(serializer);
            }
        } else {
            xqeval.setDestination(serializer);
        }

        for (XdmNode node : nodes) {
            xqeval.setContextItem(node);
            xqeval.run();
            // Even if we output an XML decl before the first node, we must not do it before any others!
            serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
        }
    }

    public static boolean xpathEqual(Processor proc, XdmItem a, XdmItem b) {
        try {
            XPathCompiler c = proc.newXPathCompiler();
            c.declareVariable(vara);
            c.declareVariable(varb);

            XPathExecutable xexec = c.compile("$vara = $varb");
            XPathSelector selector = xexec.load();

            selector.setVariable(vara,a);
            selector.setVariable(varb,b);

            Iterator<XdmItem> values = selector.iterator();
            XdmAtomicValue item = (XdmAtomicValue) values.next();
            boolean same = item.getBooleanValue();
            return same;
        } catch (SaxonApiException sae) {
            return false;
        }
    }

    // FIXME: THIS METHOD IS A GROTESQUE HACK!
    public static InputSource xdmToInputSource(XProcRuntime runtime, XdmNode node) throws SaxonApiException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Serializer serializer = runtime.getProcessor().newSerializer();
        serializer.setOutputStream(out);
        serialize(runtime, node, serializer);
        InputSource isource = new InputSource(new ByteArrayInputStream(out.toByteArray()));
        if (node.getBaseURI() != null) {
            isource.setSystemId(node.getBaseURI().toASCIIString());
        }
        return isource;
    }

    public static HashSet<String> excludeInlinePrefixes(XdmNode node, String prefixList) {
        HashSet<String> excludeURIs = new HashSet<String> ();
        excludeURIs.add(XProcConstants.NS_XPROC);

        if (prefixList != null) {
            NodeInfo inode = node.getUnderlyingNode();
            InscopeNamespaceResolver inscopeNS = new InscopeNamespaceResolver(inode);
            boolean all = false;

            for (String pfx : prefixList.split("\\s+")) {
                boolean found = false;

                if ("#all".equals(pfx)) {
                    found = true;
                    all = true;
                } else if ("#default".equals(pfx)) {
                    found = (inscopeNS.getURIForPrefix("", true) != null);
                    if (found) {
                        excludeURIs.add(inscopeNS.getURIForPrefix("", true));
                    }
                } else {
                    found = (inscopeNS.getURIForPrefix(pfx, false) != null);
                    if (found) {
                        excludeURIs.add(inscopeNS.getURIForPrefix(pfx, false));
                    }
                }

                if (!found) {
                    throw XProcException.staticError(57, node, "No binding for '" + pfx + ":'");
                }
            }

            if (all) {
                Iterator<?> pfxiter = inscopeNS.iteratePrefixes();
                while (pfxiter.hasNext()) {
                    String pfx = (String)pfxiter.next();
                    boolean def = ("".equals(pfx));
                    excludeURIs.add(inscopeNS.getURIForPrefix(pfx,def));
                }
            }
        }

        return excludeURIs;
    }

    public static XdmNode removeNamespaces(XProcRuntime runtime, XdmNode node, HashSet<String> excludeNS, boolean preserveUsed) {
        return removeNamespaces(runtime.getProcessor(), node, excludeNS, preserveUsed);
    }

    public static XdmNode removeNamespaces(Processor proc, XdmNode node, HashSet<String> excludeNS, boolean preserveUsed) {
        TreeWriter tree = new TreeWriter(proc);
        tree.startDocument(node.getBaseURI());
        removeNamespacesWriter(tree, node, excludeNS, preserveUsed);
        tree.endDocument();
        return tree.getResult();
    }

    private static void removeNamespacesWriter(TreeWriter tree, XdmNode node, HashSet<String> excludeNS, boolean preserveUsed) {
        if (node.getNodeKind() == XdmNodeKind.DOCUMENT) {
            XdmSequenceIterator iter = node.axisIterator(Axis.CHILD);
            while (iter.hasNext()) {
                XdmNode cnode = (XdmNode) iter.next();
                removeNamespacesWriter(tree, cnode, excludeNS, preserveUsed);
            }
        } else if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
            boolean usesDefaultNS = ("".equals(node.getNodeName().getPrefix())
                                     && !"".equals(node.getNodeName().getNamespaceURI()));

            NodeInfo inode = node.getUnderlyingNode();
            int nscount = 0;
            Iterator<NamespaceBinding> nsiter = NamespaceIterator.iterateNamespaces(inode);
            while (nsiter.hasNext()) {
                nscount++;
                nsiter.next();
            }

            boolean excludeDefault = false;
            boolean changed = false;
            NamespaceBinding newNS[] = null;
            if (nscount > 0) {
                newNS = new NamespaceBinding[nscount];
                int newpos = 0;
                nsiter = NamespaceIterator.iterateNamespaces(inode);
                while (nsiter.hasNext()) {
                    NamespaceBinding ns = nsiter.next();
                    String pfx = ns.getPrefix();
                    String uri = ns.getURI();

                    boolean delete = excludeNS.contains(uri);
                    excludeDefault = excludeDefault || ("".equals(pfx) && delete);

                    // You can't exclude the default namespace if it's in use
                    if ("".equals(pfx) && usesDefaultNS && preserveUsed) {
                        delete = false;
                    }

                    changed = changed || delete;

                    if (!delete) {
                        newNS[newpos++] = ns;
                    }
                }
                NamespaceBinding onlyNewNS[] = new NamespaceBinding[newpos];
                for (int pos = 0; pos < newpos; pos++) {
                    onlyNewNS[pos] = newNS[pos];
                }
                newNS = onlyNewNS;
            }

            NodeName newName = NameOfNode.makeName(inode);
            if (!preserveUsed) {
                NamespaceBinding binding = newName.getNamespaceBinding();
                if (excludeNS.contains(binding.getURI())) {
                    newName = new FingerprintedQName("", "", newName.getLocalPart());
                }
            }

            tree.addStartElement(newName, inode.getSchemaType(), newNS, node.getLineNumber());

            if (!preserveUsed) {
                // In this case, we may need to change some attributes too
                XdmSequenceIterator attriter = node.axisIterator(Axis.ATTRIBUTE);
                while (attriter.hasNext()) {
                    XdmNode attr = (XdmNode) attriter.next();
                    String attrns = attr.getNodeName().getNamespaceURI();
                    if (excludeNS.contains(attrns)) {
                        tree.addAttribute(new QName(attr.getNodeName().getLocalName()), attr.getStringValue());
                    } else {
                        tree.addAttribute(attr);
                    }
                }
            } else {
                tree.addAttributes(node);
            }

            XdmSequenceIterator iter = node.axisIterator(Axis.CHILD);
            while (iter.hasNext()) {
                XdmNode cnode = (XdmNode) iter.next();
                removeNamespacesWriter(tree, cnode, excludeNS, preserveUsed);
            }
            tree.addEndElement();
        } else {
            tree.addSubtree(node);
        }
    }

    public static void dumpTree(XdmNode tree, String message) {
        NodeInfo treeNode = tree.getUnderlyingNode();
        System.err.println(message);
        System.err.println("Dumping tree: " + treeNode.getSystemId() + ", " + tree.getBaseURI());
        XdmSequenceIterator iter = tree.axisIterator(Axis.CHILD);
        while (iter.hasNext()) {
            XdmNode child = (XdmNode) iter.next();
            dumpTreeNode(child, "  ");
        }
    }

    private static void dumpTreeNode(XdmNode node, String indent) {
        if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
            System.err.println(indent + node.getNodeName() + ": " + node.getBaseURI());
            XdmSequenceIterator iter = node.axisIterator(Axis.CHILD);
            while (iter.hasNext()) {
                XdmNode child = (XdmNode) iter.next();
                dumpTreeNode(child, indent + "  ");
            }
        } else if (node.getNodeKind() == XdmNodeKind.TEXT) {
            System.err.println(indent + "text: ...");
        }
    }

    public static boolean xpathSyntaxError(SaxonApiException sae) {
        Throwable cause = sae.getCause();
        // FIME: Is this right? Are all XPathExceptions syntax errors?
        return (cause != null && cause instanceof XPathException);
    }

    public static void assertDocument(XdmNode doc) {
        if (doc.getNodeKind() == XdmNodeKind.DOCUMENT) {
            S9apiUtils.assertDocumentContent(doc.axisIterator(Axis.CHILD));
        } else if (doc.getNodeKind() == XdmNodeKind.ELEMENT) {
            // this is ok
        } else {
            throw XProcException.dynamicError(1, "Document root cannot be " + doc.getNodeKind());
        }
    }

    public static void assertDocumentContent(XdmSequenceIterator iter) {
        int elemCount = 0;
        while (iter.hasNext()) {
            XdmNode child = (XdmNode) iter.next();
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                elemCount++;
                if (elemCount > 1) {
                    throw XProcException.dynamicError(1, "Document must have exactly one top-level element");
                }
            } else if (child.getNodeKind() == XdmNodeKind.PROCESSING_INSTRUCTION
                    || child.getNodeKind() == XdmNodeKind.COMMENT) {
                // that's ok
            } else if (child.getNodeKind() == XdmNodeKind.TEXT) {
                if (!"".equals(child.getStringValue().trim()))
                    throw XProcException.dynamicError(1, "Only whitespace text nodes can appear at the top level in a document");
            } else {
                throw XProcException.dynamicError(1, "Document cannot have top level " + child.getNodeKind());
            }
        }
    }

    // FIXME: This method exists only to work around a bug in SaxonHE 9.5.1.1
    public static XdmNode getParent(XdmNode node) {
        try {
            return node.getParent();
        } catch (ClassCastException cce) {
            return null;
        }
    }

}
