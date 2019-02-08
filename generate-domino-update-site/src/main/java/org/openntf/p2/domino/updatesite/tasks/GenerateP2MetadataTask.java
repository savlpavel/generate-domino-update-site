package org.openntf.p2.domino.updatesite.tasks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import org.eclipse.osgi.util.ManifestElement;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ProcessingInstruction;

import com.ibm.commons.util.StringUtil;
import com.ibm.commons.util.io.StreamUtil;
import com.ibm.commons.xml.DOMUtil;
import com.ibm.commons.xml.Format;
import com.ibm.commons.xml.XMLException;

public class GenerateP2MetadataTask implements Runnable {
	private final Path dest;
	
	public GenerateP2MetadataTask(Path dest) {
		this.dest = dest;
	}

	@Override
	public void run() {
		try {
			Document artifactsXml = createArtifactsXml();
			try(OutputStream os = Files.newOutputStream(dest.resolve("artifacts.xml"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				DOMUtil.serialize(os, artifactsXml, Format.defaultFormat);
			}
			Document contentXml = createContentXml();
			try(OutputStream os = Files.newOutputStream(dest.resolve("content.xml"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				DOMUtil.serialize(os, contentXml, Format.defaultFormat);
			}
			
		} catch (XMLException | IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private Document createArtifactsXml() throws IOException, XMLException {
		org.w3c.dom.Document doc = DOMUtil.createDocument();
		
		{
			ProcessingInstruction proc = doc.createProcessingInstruction("artifactRepository", "version='1.1.0'"); //$NON-NLS-1$ //$NON-NLS-2$
			doc.appendChild(proc);
		}
		
		Element repository = DOMUtil.createElement(doc, "repository"); //$NON-NLS-1$
		repository.setAttribute("name", "IBM XPages Runtime Artifacts"); //$NON-NLS-1$ //$NON-NLS-2$
		repository.setAttribute("type", "org.eclipse.equinox.p2.artifact.repository.simpleRepository"); //$NON-NLS-1$ //$NON-NLS-2$
		repository.setAttribute("version", "1"); //$NON-NLS-1$ //$NON-NLS-2$
		
		{
			Element properties = DOMUtil.createElement(doc, repository, "properties"); //$NON-NLS-1$
			properties.setAttribute("size", "2"); //$NON-NLS-1$ //$NON-NLS-2$
			
			Element timestamp = DOMUtil.createElement(doc, properties, "property"); //$NON-NLS-1$
			timestamp.setAttribute("name", "p2.timestamp"); //$NON-NLS-1$ //$NON-NLS-2$
			timestamp.setAttribute("value", String.valueOf(System.currentTimeMillis())); //$NON-NLS-1$
			
			Element compressed = DOMUtil.createElement(doc, properties, "property"); //$NON-NLS-1$
			compressed.setAttribute("name", "p2.compressed"); //$NON-NLS-1$ //$NON-NLS-2$
			compressed.setAttribute("value", "false"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		{
			Element mappings = DOMUtil.createElement(doc, repository, "mappings"); //$NON-NLS-1$
			mappings.setAttribute("size", "3"); //$NON-NLS-1$ //$NON-NLS-2$
			
			Element rule1 = DOMUtil.createElement(doc, mappings, "rule"); //$NON-NLS-1$
			rule1.setAttribute("filter", "(& (classifier=osgi.bundle))"); //$NON-NLS-1$ //$NON-NLS-2$
			rule1.setAttribute("output", "${repoUrl}/plugins/${id}_${version}.jar"); //$NON-NLS-1$ //$NON-NLS-2$
			
			Element rule2 = DOMUtil.createElement(doc, mappings, "rule"); //$NON-NLS-1$
			rule2.setAttribute("filter", "(& (classifier=binary))"); //$NON-NLS-1$ //$NON-NLS-2$
			rule2.setAttribute("output", "${repoUrl}/binary/${id}_${version}"); //$NON-NLS-1$ //$NON-NLS-2$
			
			Element rule3 = DOMUtil.createElement(doc, mappings, "rule"); //$NON-NLS-1$
			rule3.setAttribute("filter", "(& (classifier=org.eclipse.update.feature))"); //$NON-NLS-1$ //$NON-NLS-2$
			rule3.setAttribute("output", "${repoUrl}/features/${id}_${version}.jar"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		Element artifacts = DOMUtil.createElement(doc, repository, "artifacts"); //$NON-NLS-1$
		int[] size = new int[] { 0 };
		
		Files.list(dest.resolve("features")).forEach(feature -> {
			size[0]++;
			try {
				Document featureXml = getFeatureXml(feature);
				Element rootElement = featureXml.getDocumentElement();
				
				Element artifact = DOMUtil.createElement(doc, artifacts, "artifact"); //$NON-NLS-1$
				artifact.setAttribute("classifier", "org.eclipse.update.feature"); //$NON-NLS-1$ //$NON-NLS-2$
				artifact.setAttribute("id", rootElement.getAttribute("id")); //$NON-NLS-1$
				artifact.setAttribute("version", rootElement.getAttribute("version")); //$NON-NLS-1$
				
				Element properties = DOMUtil.createElement(doc, artifact, "properties"); //$NON-NLS-1$
				properties.setAttribute("size", "3"); //$NON-NLS-1$ //$NON-NLS-2$
				
				Element artifactSize = DOMUtil.createElement(doc, properties, "property"); //$NON-NLS-1$
				artifactSize.setAttribute("name", "artifact.size"); //$NON-NLS-1$ //$NON-NLS-2$
				artifactSize.setAttribute("value", String.valueOf(Files.size(feature))); //$NON-NLS-1$
				
				Element downloadSize = DOMUtil.createElement(doc, properties, "property"); //$NON-NLS-1$
				downloadSize.setAttribute("name", "download.size"); //$NON-NLS-1$ //$NON-NLS-2$
				downloadSize.setAttribute("value", String.valueOf(Files.size(feature))); //$NON-NLS-1$
				
				Element contentType = DOMUtil.createElement(doc, properties, "property"); //$NON-NLS-1$
				contentType.setAttribute("name", "download.contentType"); //$NON-NLS-1$ //$NON-NLS-2$
				contentType.setAttribute("value", Files.probeContentType(feature)); //$NON-NLS-1$
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		});
		Files.list(dest.resolve("plugins")).forEach(plugin -> {
			size[0]++;
			try {
				Manifest manifest = getPluginManifest(plugin);
				
				Element artifact = DOMUtil.createElement(doc, artifacts, "artifact"); //$NON-NLS-1$
				artifact.setAttribute("classifier", "osgi.bundle"); //$NON-NLS-1$ //$NON-NLS-2$
				String symbolicName = getPluginId(manifest);
				artifact.setAttribute("id", symbolicName); //$NON-NLS-1$
				artifact.setAttribute("version", manifest.getMainAttributes().getValue("Bundle-Version")); //$NON-NLS-1$
				
				Element properties = DOMUtil.createElement(doc, artifact, "properties"); //$NON-NLS-1$
				properties.setAttribute("size", "2"); //$NON-NLS-1$ //$NON-NLS-2$
				
				Element artifactSize = DOMUtil.createElement(doc, properties, "property"); //$NON-NLS-1$
				artifactSize.setAttribute("name", "artifact.size"); //$NON-NLS-1$ //$NON-NLS-2$
				artifactSize.setAttribute("value", String.valueOf(Files.size(plugin))); //$NON-NLS-1$
				
				Element downloadSize = DOMUtil.createElement(doc, properties, "property"); //$NON-NLS-1$
				downloadSize.setAttribute("name", "download.size"); //$NON-NLS-1$ //$NON-NLS-2$
				downloadSize.setAttribute("value", String.valueOf(Files.size(plugin))); //$NON-NLS-1$
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		});
		
		artifacts.setAttribute("size", String.valueOf(size[0])); //$NON-NLS-1$
		
		return doc;
	}
	
	private Document createContentXml() throws XMLException, IOException, DOMException {
		org.w3c.dom.Document doc = DOMUtil.createDocument();

		{
			ProcessingInstruction proc = doc.createProcessingInstruction("metadataRepository", "version='1.1.0'"); //$NON-NLS-1$ //$NON-NLS-2$
			doc.appendChild(proc);
		}
		
		Element repository = DOMUtil.createElement(doc, "repository"); //$NON-NLS-1$
		repository.setAttribute("name", "IBM XPages Runtime"); //$NON-NLS-1$
		repository.setAttribute("type", "org.eclipse.equinox.internal.p2.metadata.repository.LocalMetadataRepository"); //$NON-NLS-1$ //$NON-NLS-2$
		repository.setAttribute("version", "1"); //$NON-NLS-1$ //$NON-NLS-2$
		
		{
			Element properties = DOMUtil.createElement(doc, repository, "properties"); //$NON-NLS-1$
			properties.setAttribute("size", "2"); //$NON-NLS-1$ //$NON-NLS-2$
			
			Element timestamp = DOMUtil.createElement(doc, properties, "property"); //$NON-NLS-1$
			timestamp.setAttribute("name", "p2.timestamp"); //$NON-NLS-1$ //$NON-NLS-2$
			timestamp.setAttribute("value", String.valueOf(System.currentTimeMillis())); //$NON-NLS-1$
			
			Element compressed = DOMUtil.createElement(doc, properties, "property"); //$NON-NLS-1$
			compressed.setAttribute("name", "p2.compressed"); //$NON-NLS-1$ //$NON-NLS-2$
			compressed.setAttribute("value", "false"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		Element units = DOMUtil.createElement(doc, repository, "units"); //$NON-NLS-1$
		int size[] = new int[] { 0 };
		
		Files.list(dest.resolve("features")).forEach(feature -> {
			try {
				Document featureXml = getFeatureXml(feature);
				Element rootElement = featureXml.getDocumentElement();
				Properties props = getFeatureProperties(feature);
				
				String id = rootElement.getAttribute("id");
				String version = rootElement.getAttribute("version");
				String name = resolveWithProperties(rootElement.getAttribute("label"), props);
				
				String description = DOMUtil.evaluateXPath(doc, "/feature/description/text()").getStringValue();
				description = resolveWithProperties(description, props);
				String descriptionUrl = DOMUtil.evaluateXPath(doc, "/feature/description/@url").getStringValue();
				descriptionUrl = resolveWithProperties(descriptionUrl, props);

				String license = DOMUtil.evaluateXPath(doc, "/feature/license/text()").getStringValue();
				license = resolveWithProperties(license, props);
				String licenseUrl = DOMUtil.evaluateXPath(doc, "/feature/license/@url").getStringValue();
				licenseUrl = resolveWithProperties(licenseUrl, props);

				String copyright = DOMUtil.evaluateXPath(doc, "/feature/copyright/text()").getStringValue();
				copyright = resolveWithProperties(copyright, props);
				String copyrightUrl = DOMUtil.evaluateXPath(doc, "/feature/copyright/@url").getStringValue();
				copyrightUrl = resolveWithProperties(copyrightUrl, props);
				
				Element unit = DOMUtil.createElement(doc, units, "unit"); //$NON-NLS-1$
				unit.setAttribute("id", id + ".feature.group"); //$NON-NLS-1$ //$NON-NLS-2$
				unit.setAttribute("version", version); //$NON-NLS-1$
				
				{
					Element update = DOMUtil.createElement(doc, unit, "update"); //$NON-NLS-1$
					update.setAttribute("id", rootElement.getAttribute("id") + ".feature.group"); //$NON-NLS-1$ //$NON-NLS-2$
					update.setAttribute("range", "[0.0.0," + rootElement.getAttribute("version") + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					update.setAttribute("severity", "0"); //$NON-NLS-1$ //$NON-NLS-2$
				}
				
				{
					Element properties = DOMUtil.createElement(doc, unit, "properties"); //$NON-NLS-1$
					properties.setAttribute("size", "4"); //$NON-NLS-1$ //$NON-NLS-2$
					
					Element propName = DOMUtil.createElement(doc, properties, "property"); //$NON-NLS-1$
					propName.setAttribute("name", "org.eclipse.equinox.p2.name"); //$NON-NLS-1$ //$NON-NLS-2$
					propName.setAttribute("value", name); //$NON-NLS-1$
		
					Element propDesc = DOMUtil.createElement(doc, properties, "property"); //$NON-NLS-1$
					propDesc.setAttribute("name", "org.eclipse.equinox.p2.description"); //$NON-NLS-1$ //$NON-NLS-2$
					propDesc.setAttribute("value", description); //$NON-NLS-1$
		
					Element propDescUrl = DOMUtil.createElement(doc, properties, "property"); //$NON-NLS-1$
					propDescUrl.setAttribute("name", "org.eclipse.equinox.p2.description.url"); //$NON-NLS-1$ //$NON-NLS-2$
					propDescUrl.setAttribute("value", descriptionUrl); //$NON-NLS-1$
					
					Element propTypeGroup = DOMUtil.createElement(doc, properties, "property"); //$NON-NLS-1$
					propTypeGroup.setAttribute("name", "org.eclipse.equinox.p2.type.group"); //$NON-NLS-1$ //$NON-NLS-2$
					propTypeGroup.setAttribute("value", "true"); //$NON-NLS-1$ //$NON-NLS-2$
				}
				
				{
					Element provides = DOMUtil.createElement(doc, unit, "provides"); //$NON-NLS-1$
					provides.setAttribute("size", "1"); //$NON-NLS-1$ //$NON-NLS-2$
					
					Element provided = DOMUtil.createElement(doc, provides, "provided"); //$NON-NLS-1$
					provided.setAttribute("namespace", "org.eclipse.equinox.p2.iu"); //$NON-NLS-1$ //$NON-NLS-2$
					provided.setAttribute("name", id + ".feature.group"); //$NON-NLS-1$ //$NON-NLS-2$
					provided.setAttribute("version", version); //$NON-NLS-1$
				}
				
				{
					int requiresSize = 0;
					Element requires = DOMUtil.createElement(doc, unit, "requires"); //$NON-NLS-1$
					
					Object[] plugins = DOMUtil.evaluateXPath(doc, "/feature/plugin").getNodes();
					
					for(Object pluginObj : plugins) {
						Element plugin = (Element)pluginObj;
						
						Element required = DOMUtil.createElement(doc, requires, "required"); //$NON-NLS-1$
						required.setAttribute("namespace", "org.eclipse.equinox.p2.iu"); //$NON-NLS-1$ //$NON-NLS-2$
						required.setAttribute("name", plugin.getAttribute("id")); //$NON-NLS-1$
						required.setAttribute("range", "[" + plugin.getAttribute("version") + "," + plugin.getAttribute("version") + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
						
						requiresSize++;
					}
					
					{
						Element required = DOMUtil.createElement(doc, requires, "required"); //$NON-NLS-1$
						required.setAttribute("namespace", "org.eclipse.equinox.p2.iu"); //$NON-NLS-1$ //$NON-NLS-2$
						required.setAttribute("name", id + ".feature.jar"); //$NON-NLS-1$ //$NON-NLS-2$
						required.setAttribute("range", "[" + version + "," + version + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
						
						Element filter = DOMUtil.createElement(doc, required, "filter"); //$NON-NLS-1$
						filter.setTextContent("(org.eclipse.update.install.features=true)"); //$NON-NLS-1$
						
						requiresSize++;
					}
					
					requires.setAttribute("size", String.valueOf(requiresSize)); //$NON-NLS-1$
				}
				
				{
					Element touchpoint = DOMUtil.createElement(doc, unit, "touchpoint"); //$NON-NLS-1$
					touchpoint.setAttribute("id", "null"); //$NON-NLS-1$ //$NON-NLS-2$
					touchpoint.setAttribute("version", "0.0.0"); //$NON-NLS-1$ //$NON-NLS-2$
				}
				
				{
					Element licenses = DOMUtil.createElement(doc, unit, "licenses"); //$NON-NLS-1$
					licenses.setAttribute("size", "1"); //$NON-NLS-1$ //$NON-NLS-2$
					
					Element licenseNode = DOMUtil.createElement(doc, licenses, "license"); //$NON-NLS-1$
					licenseNode.setAttribute("uri", licenseUrl); //$NON-NLS-1$
					licenseNode.setAttribute("url", licenseUrl); //$NON-NLS-1$
					licenseNode.setTextContent(license);
				}
				
				{
					Element copyrightNode = DOMUtil.createElement(doc, unit, "copyright"); //$NON-NLS-1$
					copyrightNode.setAttribute("uri", copyrightUrl); //$NON-NLS-1$
					copyrightNode.setAttribute("url", copyrightUrl); //$NON-NLS-1$
					copyrightNode.setTextContent(copyright);
				}
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		});
		Files.list(dest.resolve("plugins")).forEach(plugin -> {
			try {
				Manifest manifest = getPluginManifest(plugin);
				Properties props = getPluginProperties(plugin);
				String id = getPluginId(manifest);
				String version = manifest.getMainAttributes().getValue("Bundle-Version");
				String name = manifest.getMainAttributes().getValue("Bundle-Name");
				name = resolveWithProperties(name, props);
				String provider = manifest.getMainAttributes().getValue("Bundle-Vendor");
				provider = resolveWithProperties(provider, props);
				boolean fragment = StringUtil.isNotEmpty(manifest.getMainAttributes().getValue("Fragment-Host"));
				
				Element unit = DOMUtil.createElement(doc, units, "unit"); //$NON-NLS-1$
				unit.setAttribute("id", id); //$NON-NLS-1$
				unit.setAttribute("version", version); //$NON-NLS-1$
				
				{
					Element update = DOMUtil.createElement(doc, unit, "update"); //$NON-NLS-1$
					update.setAttribute("id", id); //$NON-NLS-1$ //$NON-NLS-2$
					update.setAttribute("range", "[0.0.0," + version + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					update.setAttribute("severity", "0"); //$NON-NLS-1$ //$NON-NLS-2$
				}
				
				{
					Element properties = DOMUtil.createElement(doc, unit, "properties"); //$NON-NLS-1$
					properties.setAttribute("size", "2"); //$NON-NLS-1$ //$NON-NLS-2$
					
					Element propName = DOMUtil.createElement(doc, properties, "property"); //$NON-NLS-1$
					propName.setAttribute("name", "org.eclipse.equinox.p2.name"); //$NON-NLS-1$ //$NON-NLS-2$
					propName.setAttribute("value", name); //$NON-NLS-1$
					
					Element propProvider = DOMUtil.createElement(doc, properties, "provider"); //$NON-NLS-1$
					propProvider.setAttribute("name", "org.eclipse.equinox.p2.provider"); //$NON-NLS-1$ //$NON-NLS-2$
					propProvider.setAttribute("value", provider); //$NON-NLS-1$
				}
				
				{
					int[] providedSize = new int[] { 0 };
					Element provides = DOMUtil.createElement(doc, unit, "provides"); //$NON-NLS-1$
					
					Element providedIu = DOMUtil.createElement(doc, provides, "provided"); //$NON-NLS-1$
					providedIu.setAttribute("namespace", "org.eclipse.equinox.p2.iu"); //$NON-NLS-1$ //$NON-NLS-2$
					providedIu.setAttribute("name", id); //$NON-NLS-1$
					providedIu.setAttribute("version", version); //$NON-NLS-1$
					
					Element providedBundle = DOMUtil.createElement(doc, provides, "provided"); //$NON-NLS-1$
					providedBundle.setAttribute("namespace", "osgi.bundle"); //$NON-NLS-1$ //$NON-NLS-2$
					providedBundle.setAttribute("name", id); //$NON-NLS-1$
					providedBundle.setAttribute("version", version); //$NON-NLS-1$
					
					Element providedType = DOMUtil.createElement(doc, provides, "provided"); //$NON-NLS-1$
					providedType.setAttribute("namespace", "org.eclipse.equinox.p2.eclipse.type"); //$NON-NLS-1$ //$NON-NLS-2$
					providedType.setAttribute("name", "bundle"); //$NON-NLS-1$ //$NON-NLS-2$
					providedType.setAttribute("version", "1.0.0"); //$NON-NLS-1$ //$NON-NLS-2$
					
					String exportPackage = manifest.getMainAttributes().getValue("Export-Package");
					if(StringUtil.isNotEmpty(exportPackage)) {
						ManifestElement[] elements = ManifestElement.parseHeader("Export-Package", exportPackage);
						for(ManifestElement el : elements) {
							providedSize[0]++;
							
							String packageVersion = el.getAttribute("version");
							if(StringUtil.isEmpty(packageVersion)) {
								packageVersion = "0.0.0";
							}
							Element exportElement = DOMUtil.createElement(doc, provides, "provided");
							exportElement.setAttribute("namespace", "java.package");
							exportElement.setAttribute("name", el.getValue());
							exportElement.setAttribute("version", packageVersion);
						}
					}
					
					
					if(fragment) {
						providedSize[0]++;
						String hostHeader = manifest.getMainAttributes().getValue("Fragment-Host");
						String[] parts = StringUtil.splitString(hostHeader, ';');
						String host = parts[0];
						String hostVersion = Stream.of(parts)
								.filter(p -> p.startsWith("bundle-version="))
								.findFirst()
								.map(p -> p.substring("bundle-version=".length()+1))
								.orElse("");
						if(StringUtil.isNotEmpty(hostVersion)) {
							if(hostVersion.startsWith("\"")) {
								hostVersion = hostVersion.substring(1, hostVersion.length()-1);
							}
						} else {
							Optional<String> hostPlugin = Files.list(dest.resolve("plugins"))
									.map(Path::getFileName)
									.map(Path::toString)
									.filter(p -> p.startsWith(host))
									.findFirst();
							if(hostPlugin.isPresent()) {
								hostVersion = hostPlugin.get().substring(host.length()+1, hostPlugin.get().length()-".jar".length());
							} else {
								hostVersion = "0.0.0"; //$NON-NLS-1$
							}
						}
						
						Element providedFragment = DOMUtil.createElement(doc, provides, "provided"); //$NON-NLS-1$
						providedFragment.setAttribute("namespace", "osgi.fragment"); //$NON-NLS-1$ //$NON-NLS-2$
						providedFragment.setAttribute("name", host); //$NON-NLS-1$
						providedFragment.setAttribute("version", hostVersion); //$NON-NLS-1$
					}
					provides.setAttribute("size", String.valueOf(providedSize[0])); //$NON-NLS-1$
				}
				
				{
					Element requires = DOMUtil.createElement(doc, unit, "requires"); //$NON-NLS-1$
					int[] requiresCount = new int[] { 0 };
					
					String requireBundle = manifest.getMainAttributes().getValue("Require-Bundle");
					if(StringUtil.isNotEmpty(requireBundle)) {
						ManifestElement[] elements = ManifestElement.parseHeader("Require-Bundle", requireBundle);
						for(ManifestElement el : elements) {
							Element required = DOMUtil.createElement(doc, requires, "required"); //$NON-NLS-1$
							required.setAttribute("namespace", "osgi.bundle"); //$NON-NLS-1$ //$NON-NLS-2$
							
							String requireVersion = el.getAttribute("bundle-version");
							if(StringUtil.isEmpty(requireVersion)) {
								requireVersion = "0.0.0";
							}
							boolean optional = "optional".equals(el.getDirective("resolution"));
							
							required.setAttribute("name", el.getValue()); //$NON-NLS-1$
							required.setAttribute("range", requireVersion); //$NON-NLS-1$
							required.setAttribute("optional", Boolean.toString(optional));
						}
					}
					
					String importPackages = manifest.getMainAttributes().getValue("Import-Package");
					if(StringUtil.isNotEmpty(importPackages)) {
						ManifestElement[] elements = ManifestElement.parseHeader("Import-Package", importPackages);
						for(ManifestElement el : elements) {
							Element required = DOMUtil.createElement(doc, requires, "required"); //$NON-NLS-1$
							required.setAttribute("namespace", "java.package"); //$NON-NLS-1$ //$NON-NLS-2$
							
							String requireVersion = el.getAttribute("version");
							if(StringUtil.isEmpty(requireVersion)) {
								requireVersion = "0.0.0";
							}
							boolean optional = "optional".equals(el.getDirective("resolution"));
							
							required.setAttribute("name", el.getValue()); //$NON-NLS-1$
							required.setAttribute("range", requireVersion); //$NON-NLS-1$
							required.setAttribute("optional", Boolean.toString(optional));
						}
					}
					
					requires.setAttribute("size", String.valueOf(requiresCount[0])); //$NON-NLS-1$
				}
				
				{
					Element artifacts = DOMUtil.createElement(doc, unit, "artifacts"); //$NON-NLS-1$
					artifacts.setAttribute("size", "1"); //$NON-NLS-1$ //$NON-NLS-2$
					
					Element artifact = DOMUtil.createElement(doc, artifacts, "artifact"); //$NON-NLS-1$
					artifact.setAttribute("classifier", "osgi.bundle"); //$NON-NLS-1$ //$NON-NLS-2$
					artifact.setAttribute("id", id); //$NON-NLS-1$
					artifact.setAttribute("version", version); //$NON-NLS-1$
				}
				
				{
					Element touchpoint = DOMUtil.createElement(doc, unit, "touchpoint"); //$NON-NLS-1$
					touchpoint.setAttribute("id", "org.eclipse.equinox.p2.osgi"); //$NON-NLS-1$ //$NON-NLS-2$
					touchpoint.setAttribute("version", "1.0.0"); //$NON-NLS-1$ //$NON-NLS-2$
				}
				{
					Element touchpointData = DOMUtil.createElement(doc, unit, "touchpointData"); //$NON-NLS-1$
					touchpointData.setAttribute("size", "1"); //$NON-NLS-1$ //$NON-NLS-2$
					
					Element instructions = DOMUtil.createElement(doc, touchpointData, "instructions"); //$NON-NLS-1$
					instructions.setAttribute("size", "2"); //$NON-NLS-1$ //$NON-NLS-2$
					
					Element instZipped = DOMUtil.createElement(doc, instructions, "instruction"); //$NON-NLS-1$
					instZipped.setAttribute("key", "zipped"); //$NON-NLS-1$ //$NON-NLS-2$
					instZipped.setTextContent("false"); //$NON-NLS-1$
					
					Element instManifest = DOMUtil.createElement(doc, instructions, "manifest"); //$NON-NLS-1$
					instManifest.setAttribute("key", "manifest"); //$NON-NLS-1$ //$NON-NLS-2$
					// TODO trim this down?
					try(JarFile jar = new JarFile(plugin.toFile())) {
						ZipEntry manifestEntry = jar.getEntry("META-INF/MANIFEST.MF");
						try(InputStream is = jar.getInputStream(manifestEntry)) {
							instManifest.setTextContent(StreamUtil.readString(is));
						}
					}
				}
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		});
		

		units.setAttribute("size", String.valueOf(size[0])); //$NON-NLS-1$
		
		return doc;
	}

	private Document getFeatureXml(Path feature) {
		try {
			try(JarFile featureJar = new JarFile(feature.toFile())) {
				ZipEntry xmlEntry = featureJar.getEntry("feature.xml");
				try(InputStream is = featureJar.getInputStream(xmlEntry)) {
					return DOMUtil.createDocument(is);
				}
			}
		} catch(IOException | XMLException e) {
			throw new RuntimeException(e);
		}
	}
	
	private Properties getFeatureProperties(Path feature) {
		try {
			try(JarFile jar = new JarFile(feature.toFile())) {
				ZipEntry entry = jar.getEntry("feature.properties");
				if(entry == null) {
					return new Properties();
				}
				try(InputStream is = jar.getInputStream(entry)) {
					Properties result = new Properties();
					result.load(is);
					return result;
				}
			}
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private Manifest getPluginManifest(Path plugin) {
		try {
			try(JarFile pluginJar = new JarFile(plugin.toFile())) {
				return pluginJar.getManifest();
			}
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private Properties getPluginProperties(Path plugin) {
		try {
			try(JarFile jar = new JarFile(plugin.toFile())) {
				ZipEntry entry = jar.getEntry("plugin.properties");
				if(entry == null) {
					entry = jar.getEntry("fragment.properties");
				}
				if(entry == null) {
					return new Properties();
				}
				try(InputStream is = jar.getInputStream(entry)) {
					Properties result = new Properties();
					result.load(is);
					return result;
				}
			}
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private String resolveWithProperties(String value, Properties properties) {
		String v = StringUtil.toString(value);
		for(Map.Entry<Object, Object> prop : properties.entrySet()) {
			String key = StringUtil.toString(prop.getKey());
			String val = StringUtil.toString(prop.getValue());
			v = v.replace("%" + key, val);
		}
		return v;
	}
	
	private String getPluginId(Manifest manifest) {
		String symbolicName = manifest.getMainAttributes().getValue("Bundle-SymbolicName");
		int semiIndex = symbolicName.indexOf(';');
		if(semiIndex > -1) {
			symbolicName = symbolicName.substring(0, semiIndex);
		}
		return symbolicName;
	}
}
