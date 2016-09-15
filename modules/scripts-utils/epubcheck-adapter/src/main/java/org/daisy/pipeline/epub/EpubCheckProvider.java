package org.daisy.pipeline.epub;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import org.daisy.common.xproc.calabash.XProcStepProvider;

import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.core.XProcStep;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.library.DefaultStep;
import com.xmlcalabash.runtime.XAtomicStep;

import com.adobe.epubcheck.api.EpubCheck;
import com.adobe.epubcheck.api.EpubCheckFactory;
import com.adobe.epubcheck.api.Report;
import com.adobe.epubcheck.nav.NavCheckerFactory;
import com.adobe.epubcheck.opf.DocumentValidator;
import com.adobe.epubcheck.opf.DocumentValidatorFactory;
import com.adobe.epubcheck.opf.OPFCheckerFactory;
import com.adobe.epubcheck.ops.OPSCheckerFactory;
import com.adobe.epubcheck.overlay.OverlayCheckerFactory;
import com.adobe.epubcheck.util.*;

public class EpubCheckProvider implements XProcStepProvider {

	private static HashMap<OPSType, String> modeMimeTypeMap;

	static {
		HashMap<OPSType, String> map = new HashMap<OPSType, String>();

		map.put(new OPSType("xhtml", EPUBVersion.VERSION_2), "application/xhtml+xml");
		map.put(new OPSType("xhtml", EPUBVersion.VERSION_3), "application/xhtml+xml");

		map.put(new OPSType("svg", EPUBVersion.VERSION_2), "image/svg+xml");
		map.put(new OPSType("svg", EPUBVersion.VERSION_3), "image/svg+xml");

		map.put(new OPSType("mo", EPUBVersion.VERSION_3), "application/smil+xml");
		map.put(new OPSType("nav", EPUBVersion.VERSION_3), "nav");

		modeMimeTypeMap = map;
	}

	private static HashMap<OPSType, DocumentValidatorFactory> documentValidatorFactoryMap;

	static {
		HashMap<OPSType, DocumentValidatorFactory> map = new HashMap<OPSType, DocumentValidatorFactory>();
		map.put(new OPSType(null, EPUBVersion.VERSION_2), EpubCheckFactory.getInstance());
		map.put(new OPSType(null, EPUBVersion.VERSION_3), EpubCheckFactory.getInstance());

		map.put(new OPSType("opf", EPUBVersion.VERSION_2), OPFCheckerFactory.getInstance());
		map.put(new OPSType("opf", EPUBVersion.VERSION_3), OPFCheckerFactory.getInstance());

		map.put(new OPSType("xhtml", EPUBVersion.VERSION_2), OPSCheckerFactory.getInstance());
		map.put(new OPSType("xhtml", EPUBVersion.VERSION_3), OPSCheckerFactory.getInstance());

		map.put(new OPSType("svg", EPUBVersion.VERSION_2), OPSCheckerFactory.getInstance());
		map.put(new OPSType("svg", EPUBVersion.VERSION_3), OPSCheckerFactory.getInstance());

		map.put(new OPSType("mo", EPUBVersion.VERSION_3), OverlayCheckerFactory.getInstance());
		map.put(new OPSType("nav", EPUBVersion.VERSION_3), NavCheckerFactory.getInstance());

		documentValidatorFactoryMap = map;
	}

	public XProcStep newStep(XProcRuntime runtime, XAtomicStep step) {
		return new EpubCheckStep(runtime, step);
	}

	public static class EpubCheckStep extends DefaultStep {

		private static final QName _epubFile = new QName("epub");
		private static final QName _epubVersion = new QName("version");
		private static final QName _mode = new QName("mode"); // epub/opf/xhtml/svg/mo/nav

		private WritablePipe report = null;

		private EpubCheckStep(XProcRuntime runtime, XAtomicStep step) {
			super(runtime, step);
		}

		@Override
		public void setOutput(String port, WritablePipe pipe) {
			report = pipe;
		}

		@Override
		public void reset() {
			report.resetWriter();
		}

		@Override
		public void run() throws SaxonApiException {
			super.run();

			Archive epub = null;
			try {

				URI epubURI = new URI(getOption(_epubFile).getString());
				File epubFile = new File(epubURI);
				String path = epubFile.getCanonicalPath();

				String epubVersionString = getOption(_epubVersion).getString();

				if (!("3".equals(epubVersionString) || "2".equals(epubVersionString))) {
					throw new InvalidVersionException("'"+_epubVersion.getLocalName()+"' should be either '2' or '3'");
				}

				EPUBVersion epubVersion = EPUBVersion.VERSION_2.equals(epubVersionString) ? EPUBVersion.VERSION_2 : EPUBVersion.VERSION_3;

				String mode = getOption(_mode).getString();
				if ("epub".equals(mode))
					mode = null;

				File fileOut = File.createTempFile("epubcheck", null);
				Report xmlReport = new XmlReportImpl(fileOut, epubFile.getName(), EpubCheck.version());

				String toolDate = EpubCheck.buildDate();
				if (toolDate != null && !toolDate.startsWith("$"))
					xmlReport.info(null, FeatureEnum.TOOL_DATE, toolDate);

				if (mode != null) {
					xmlReport.info(null, FeatureEnum.EXEC_MODE, String.format(Messages.SINGLE_FILE, mode, epubVersion.toString()));

					if ("expanded".equals(mode) || "exp".equals(mode)) {
						epub = new Archive(path, false);
						epub.createArchive();

						EpubCheck check = new EpubCheck(epub.getEpubFile(), xmlReport, epubVersion);
						check.validate();
					}

				}

				if (epub == null) {
					GenericResourceProvider resourceProvider;

					if (path.startsWith("http://") || path.startsWith("https://"))
						resourceProvider = new URLResourceProvider(path);
					else
						resourceProvider = new FileResourceProvider(path);

					OPSType opsType = new OPSType(mode, epubVersion);

					DocumentValidatorFactory factory = (DocumentValidatorFactory) documentValidatorFactoryMap.get(opsType);

					if (factory == null) {
						xmlReport.exception(
								path,
								new RuntimeException(String.format(Messages.MODE_VERSION_NOT_SUPPORTED, mode, epubVersion))
								);

						throw new RuntimeException(String.format(
								Messages.MODE_VERSION_NOT_SUPPORTED, mode, epubVersion));
					}

					DocumentValidator check = factory.newInstance(xmlReport, path,
							resourceProvider, (String) modeMimeTypeMap.get(opsType),
							epubVersion);
					check.validate();
				}

				if (((XmlReportImpl) xmlReport).generate()) {
					XdmNode reportXml = runtime.getProcessor().newDocumentBuilder().build(fileOut);
					fileOut.delete();
					report.write(reportXml);
				} else {
					throw new Exception("An error occured while trying to write epubcheck report to: "+fileOut.getAbsolutePath());
				}
			}

			catch (Exception e) {
				throw new XProcException(step.getNode(), e);
			}

			finally {
				if (epub != null)
					epub.deleteEpubFile();
			}
		}
	}
}
