package org.aes.event;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

import java.nio.file.Path;
import java.util.List;

@Getter
@Setter
public class FileDeletionEvent extends ApplicationEvent {
	
	private transient List<Path> deletionFilesList;
	
	public FileDeletionEvent(List<Path> deletionFilesList) {
		super(deletionFilesList);
		this.deletionFilesList = deletionFilesList;
	}
	
}
