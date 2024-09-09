package de.intranda.goobi.plugins;

import java.io.IOException;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.log4j.Logger;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.dl.Prefs;
import ugh.dl.Metadata;
import ugh.dl.Person;
import ugh.dl.MetadataType;
import ugh.dl.MetadataGroup;
import ugh.exceptions.WriteException;
import ugh.dl.DocStructType;

import de.sub.goobi.helper.exceptions.DAOException;
import ugh.dl.ContentFile;
import ugh.dl.Reference;
import ugh.dl.RomanNumeral;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;

import de.sub.goobi.metadaten.MetadatenHelper;
import de.sub.goobi.metadaten.MetadatumImpl;
import de.sub.goobi.metadaten.MetaPerson;
import de.sub.goobi.metadaten.MetadataGroupImpl;
import de.sub.goobi.metadaten.Metadaten;

import org.goobi.beans.Process;

@PluginImplementation
@Log4j2
public class MetsEnhancerStepPlugin implements IStepPluginVersion2 {

	private static final long serialVersionUID = 1L;
	@Getter
	private String title = "intranda_step_mets_enhancer";
	private static final Logger logger = Logger.getLogger(MetsEnhancerStepPlugin.class);
	@Getter
	private Step step;
	@Getter
	private Boolean createPagination;
	@Getter 
	private String configCollection;
	@Getter
	private String paginationType;
	@Getter
	private boolean allowTaskFinishButtons;
	private String returnPath;
	private Process process;
	private Prefs prefs;
	private SubnodeConfiguration config;
	
	@Override
	public void initialize(Step step, String returnPath) {
		this.returnPath = returnPath;
		this.step = step;
		process = step.getProzess();

		// read parameters from correct block in configuration file
		config = ConfigPlugins.getProjectAndStepConfig(title, step);
		// Get pagination type if pagination is enabled
		createPagination = config.getBoolean("createPagination", false);
		SubnodeConfiguration paginationTypeConfig = config.configurationAt("createPagination");
		 // Retrieve the collection value out of the config
		paginationType = paginationTypeConfig.getString("@type");
		configCollection = config.getString("collection", null);
		// Get process preferences (rule sets)
		prefs = process.getRegelsatz().getPreferences();
		allowTaskFinishButtons = config.getBoolean("allowTaskFinishButtons", false);
		log.info("MetsEnhancer step plugin initialized");
	}

	@Override
	public PluginGuiType getPluginGuiType() {
		return PluginGuiType.NONE;
	}

	@Override
	public String getPagePath() {
		return "/uii/plugin_step_mets_enhancer.xhtml";
	}

	@Override
	public PluginType getType() {
		return PluginType.Step;
	}

	@Override
	public String cancel() {
		return "/uii" + returnPath;
	}

	@Override
	public String finish() {
		return "/uii" + returnPath;
	}

	@Override
	public int getInterfaceVersion() {
		return 0;
	}

	@Override
	public HashMap<String, StepReturnValue> validate() {
		return null;
	}

	@Override
	public boolean execute() {
		try {
			// Read the metadata file of the process
			Fileformat ff = process.readMetadataFile();
			DigitalDocument dd = ff.getDigitalDocument();
			// Retrieve logical and physical structure elements from the document
			DocStruct rootElement = dd.getLogicalDocStruct();
			DocStruct physicalElement = dd.getPhysicalDocStruct();
			MetadatenHelper metadatenHelper = new MetadatenHelper(prefs, dd);

			// Add images to mets file
			createDefaultValues(metadatenHelper, rootElement);

			// Add pagination if wanted
			if (createPagination) {
				createPagination(physicalElement, rootElement, dd);
			}
			// Check if there is already a "singleDigCollection" value and if not replace it with the collections value in the config
			MetadataType mdt = prefs.getMetadataTypeByName("singleDigCollection");
            List<? extends Metadata> mdList = rootElement.getAllMetadataByType(mdt);
            if (mdList == null || mdList.isEmpty()) {

                Metadata collection = new Metadata(mdt);
                collection.setValue(configCollection);
                rootElement.addMetadata(collection);
            }

			// Add Metadata out of the config
			List<HierarchicalConfiguration> metadataConfigs = config.configurationsAt("addMetadata");
			for (HierarchicalConfiguration metadataConfig : metadataConfigs) {
				String type = metadataConfig.getString("@type");
				String value = metadataConfig.getString("@value");
				MetadataType mdt2 = prefs.getMetadataTypeByName(type);
				Metadata collection = new Metadata(mdt2);
				collection.setValue(value);
				rootElement.addMetadata(collection);
			}
			process.writeMetadataFile(ff);
		} catch (PreferencesException | ReadException | IOException | SwapException | TypeNotAllowedForParentException
				| InterruptedException | DAOException | MetadataTypeNotAllowedException | WriteException e) {
			logger.error(e);
			Helper.setFehlerMeldung(e);
			return false;
		}
		return true;
	}

	private void createDefaultValues(MetadatenHelper metahelper, DocStruct element) {
		LinkedList<MetadatumImpl> lsMeta = new LinkedList<>();
		LinkedList<MetaPerson> lsPers = new LinkedList<>();
		List<MetadataGroupImpl> metaGroups = new LinkedList<>();

		// Get all metadata and default display values
		List<? extends Metadata> myTempMetadata = metahelper.getMetadataInclDefaultDisplay(element, "de",
				Metadaten.MetadataTypes.METADATA, process, true);
		if (myTempMetadata != null) {
			for (Metadata metadata : myTempMetadata) {
				MetadatumImpl meta = new MetadatumImpl(metadata, 0, prefs, process, null);
				meta.getSelectedItem();
				lsMeta.add(meta);
			}
		}
		// Get all persons and the default values
		myTempMetadata = metahelper.getMetadataInclDefaultDisplay(element, "de", Metadaten.MetadataTypes.PERSON,
				process, true);
		if (myTempMetadata != null) {
			for (Metadata metadata : myTempMetadata) {
				lsPers.add(new MetaPerson((Person) metadata, 0, prefs, element, process, null));
			}
		}
		
		// Process metadata groups and add default values
		List<MetadataGroup> groups = metahelper.getMetadataGroupsInclDefaultDisplay(element, "de", process);
		if (groups != null) {
			for (MetadataGroup mg : groups) {
				for (Metadata md : mg.getMetadataList()) {
					if (StringUtils.isBlank(md.getValue())) {
						// Set default blank value if metadata value is empty
						md.setValue("");
					}
				}
				MetadataGroupImpl mgi = new MetadataGroupImpl(prefs, process, mg, null, "", "", 0);
				metaGroups.add(mgi);
				for (MetadatumImpl meta : mgi.getMetadataList()) {
					meta.getSelectedItem();
				}
			}
		}
		// Recursively process all children elements
		if (element.getAllChildren() != null && element.getAllChildren().size() > 0) {
			for (DocStruct ds : element.getAllChildren()) {
				createDefaultValues(metahelper, ds);
			}
		}
	}

	public void createPagination(DocStruct physicaldocstruct, DocStruct log, DigitalDocument dd)
			throws TypeNotAllowedForParentException, IOException, SwapException, InterruptedException, DAOException {
		MetadataType MDTypeForPath = prefs.getMetadataTypeByName("pathimagefiles");
		// Create a tree if there is not already one
		if (physicaldocstruct == null) {
			DocStructType dst = prefs.getDocStrctTypeByName("BoundBook");
			physicaldocstruct = dd.createDocStruct(dst);
			dd.setPhysicalDocStruct(physicaldocstruct);
		}

		// Check and set valid file path for images
		try {
			List<? extends Metadata> filepath = physicaldocstruct.getAllMetadataByType(MDTypeForPath);
			Metadata mdForPath;
			if (filepath == null || filepath.isEmpty()) {
				mdForPath = new Metadata(MDTypeForPath);
				physicaldocstruct.addMetadata(mdForPath);
			} else {
				mdForPath = filepath.get(0);
			}
			mdForPath.setValue("file://" + process.getImagesTifDirectory(false));

		} catch (Exception e) {
			logger.error(e);
		}

		// Retrieve and sort image files from the process's image directory
		File imagesDirectory = new File(process.getImagesTifDirectory(false));
		if (imagesDirectory.isDirectory()) {
			List<File> imageFiles = Arrays.asList(imagesDirectory.listFiles(new FilenameFilter() {

				@Override
				public boolean accept(File dir, String name) {
					return name.toLowerCase().endsWith("tif") || name.toLowerCase().endsWith("tiff")
							|| name.toLowerCase().endsWith("jpg") || name.toLowerCase().endsWith("jpeg")
							|| name.toLowerCase().endsWith("jp2") || name.toLowerCase().endsWith("png")
							|| name.toLowerCase().endsWith("pdf");
				}
			}));

			Collections.sort(imageFiles);

			// Clean up old references to pages
			if (physicaldocstruct.getAllChildren() != null) {
				List<DocStruct> pages = physicaldocstruct.getAllChildren();

				for (DocStruct page : pages) {

					dd.getFileSet().removeFile(page.getAllContentFiles().get(0));

					List<Reference> refs = new ArrayList<>(page.getAllFromReferences());
					for (ugh.dl.Reference ref : refs) {
						ref.getSource().removeReferenceTo(page);
					}
				}
			}
			while (physicaldocstruct.getAllChildren() != null && !physicaldocstruct.getAllChildren().isEmpty()) {
				physicaldocstruct.removeChild(physicaldocstruct.getAllChildren().get(0));
			}

			// Add new pages
			int pageNo = 0;
			for (File file : imageFiles) {
				pageNo++;
				addPage(physicaldocstruct, log, dd, file, pageNo);

			}
		}
	}

	private void addPage(DocStruct physicaldocstruct, DocStruct log, DigitalDocument dd, File imageFile, int pageNo)
			throws TypeNotAllowedForParentException, IOException, InterruptedException, SwapException, DAOException {

		// Create a new page document structure
		DocStructType newPage = prefs.getDocStrctTypeByName("page");
		DocStruct dsPage = dd.createDocStruct(newPage);
		try {
			// Add physical page number metadata
			physicaldocstruct.addChild(dsPage);
			MetadataType mdt = prefs.getMetadataTypeByName("physPageNumber");
			Metadata mdTemp = new Metadata(mdt);
			mdTemp.setValue(String.valueOf(pageNo));
			dsPage.addMetadata(mdTemp);

			// Add logical page number metadata based on pagination type
			mdt = prefs.getMetadataTypeByName("logicalPageNumber");
			mdTemp = new Metadata(mdt);
			
			if(paginationType.equals("uncounted")) {
				mdTemp.setValue("uncounted");
			} else if(paginationType.equals("ROMAN")) {
				RomanNumeral roman = new RomanNumeral();
				roman.setValue(pageNo);
				mdTemp.setValue(roman.getNumber());	
			} else if(paginationType.equals("roman")) {
				RomanNumeral roman = new RomanNumeral();
				roman.setValue(pageNo);
				mdTemp.setValue(roman.getNumber().toLowerCase());	
			} else if(paginationType.equals("arabic")) {
				mdTemp.setValue(String.valueOf(pageNo));
			}
			
			dsPage.addMetadata(mdTemp);
			log.addReferenceTo(dsPage, "logical_physical");

			 // Add the image file to the page
			ContentFile cf = new ContentFile();

			cf.setLocation("file://" + imageFile.getAbsolutePath());

			dsPage.addContentFile(cf);

		} catch (TypeNotAllowedAsChildException e) {
			logger.error(e);
		} catch (MetadataTypeNotAllowedException e) {
			logger.error(e);
		}
	}

	@Override
	public PluginReturnValue run() {
		boolean successful = true;
		// your logic goes here

		log.info("MetsEnhancer step plugin executed");
		if (!successful) {
			return PluginReturnValue.ERROR;
		}
		return PluginReturnValue.FINISH;
	}
}