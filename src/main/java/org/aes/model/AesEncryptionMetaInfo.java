package org.aes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.crypto.SecretKey;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "aes_encryption_meta_info")
public class AesEncryptionMetaInfo {
	
	@Id
	@Column(name = "id", nullable = false)
	private String id;
	
	@Column(name = "secret_key")
	private SecretKey secretKey;
	
	@Column(name = "iv")
	private byte[] iv;
	
	@Column(name = "tag_length")
	private int tagLength;
	
}
