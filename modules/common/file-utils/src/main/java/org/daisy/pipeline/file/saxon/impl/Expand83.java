package org.daisy.pipeline.file.saxon.impl;

import java.io.File;
import java.net.URI;
import java.net.URL;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import org.daisy.common.file.URLs;

import org.osgi.service.component.annotations.Component;

@Component(
	name = "pf:file-expand83",
	service = { ExtensionFunctionDefinition.class }
)
@SuppressWarnings("serial")
public class Expand83 extends ExtensionFunctionDefinition {

	private static final StructuredQName funcname = new StructuredQName("pf",
			"http://www.daisy.org/ns/pipeline/functions", "file-expand83");

	@Override
	public SequenceType[] getArgumentTypes() {
		return new SequenceType[] { SequenceType.SINGLE_STRING };
	}

	@Override
	public StructuredQName getFunctionQName() {
		return funcname;
	}

	@Override
	public SequenceType getResultType(SequenceType[] arg0) {
		return SequenceType.SINGLE_STRING;
	}

	@Override
	public ExtensionFunctionCall makeCallExpression() {
		return new ExtensionFunctionCall() {

			@Override
			public Sequence call(XPathContext context, Sequence[] arguments)
					throws XPathException {
				String uri = ((AtomicSequence) arguments[0]).getStringValue();
				uri = Expand83.expand83(uri);
				return new StringValue(uri, BuiltInAtomicType.STRING);
			}
		};
	}

	/**
	 * Expands 8.3 encoded path segments.
	 *
	 * For instance `C:\DOCUME~1\file.xml` will become `C:\Documents and
	 * Settings\file.xml`
	 */
	public static String expand83(String uri) throws XPathException {
		if (uri == null || !uri.startsWith("file:/")) {
			return uri;
		}
		return expand83(URLs.asURI(uri)).toASCIIString();
	}
	
	private static URI expand83(URI uri) throws XPathException {
		try {
			String protocol = "file";
			String path = uri.getPath();
			String zipPath = null;
			if (path.contains("!/")) {
				// it is a path to a ZIP entry
				zipPath = path.substring(path.indexOf("!/")+1);
				path = path.substring(0, path.indexOf("!/"));
			}
			String query = uri.getQuery();
			String fragment = uri.getFragment();
			File file = new File(new URI(protocol, null, path, null, null));
			URI expandedUri = expand83(file, path.endsWith("/"));
			if (expandedUri == null) {
				return uri;
			} else {
				path = expandedUri.getPath();
				if (zipPath != null)
					path = path + "!" + new URI(null, null, zipPath, null, null).getPath();
				return new URI(protocol, null, path, query, fragment);
			}
		} catch (Throwable e) {
			throw new XPathException("Unexpected error in pf:file-expand83("+uri+")", e);
		}
	}

	/**
	 * this is extracted out of `expand83(String)` because it can be unit tested
	 * with a custom File implementation.
	 */
	public static URI expand83(File file) throws XPathException {
		return expand83(file, false);
	}
	
	public static URI expand83(File file, boolean isDir) throws XPathException {
		try {
			if (file.exists()) {
				return URLs.asURI(file.getCanonicalFile());
			} else {
				// if the file does not exist a parent directory may exist which can be canonicalized
				String relPath = file.getName();
				if (isDir)
					relPath += "/";
				File dir = file.getParentFile();
				while (dir != null) {
					if (dir.exists())
						return URLs.resolve(URLs.asURI(dir.getCanonicalFile()), new URI(null, null, relPath, null, null));
					relPath = dir.getName() + "/" + relPath;
					dir = dir.getParentFile();
				}
				return URLs.asURI(file);
			}
		} catch (Throwable e) {
			throw new XPathException("Unexpected error in pf:file-expand83("+file+")", e);
		}
	}

}
