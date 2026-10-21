package com.dcb.object.to.word.api;

import java.io.File;

public interface ObjectEntryWordExporter {

	public File export(
			long companyId, String objectDefinitionERC, long objectEntryId)
		throws Exception;

}
