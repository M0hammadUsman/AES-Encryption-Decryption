package org.aes.eventlistner;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.aes.event.FileDeletionEvent;
import org.aes.service.DeletionService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Getter
@Setter
@Component
public class FileDeletionEventListener {
	
	private final DeletionService deletionService;
	private List<Path> deletionFilesList = new ArrayList<>();
	
	@EventListener
	public void onFileDeletionEvent(FileDeletionEvent fileDeletionEvent) {
		deletionFilesList.addAll(fileDeletionEvent.getDeletionFilesList());
		var deletionFilesListCopy = new ArrayList<>(deletionFilesList);
		deletionFilesList.clear();
		deletionService.deleteTempFiles(deletionFilesListCopy);
	}
	
}
