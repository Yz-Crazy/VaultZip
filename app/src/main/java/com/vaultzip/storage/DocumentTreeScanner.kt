package com.vaultzip.storage

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.vaultzip.archive.model.ArchivePartSource
import com.vaultzip.archive.model.ArchiveSelection
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentTreeScanner @Inject constructor(
    @ApplicationContext private val context: Context
) : ArchiveSiblingScanner {

    override suspend fun scanSiblings(selection: ArchiveSelection): List<ArchivePartSource> {
        return when (selection) {
            is ArchiveSelection.TreeDocument -> {
                val root = DocumentFile.fromTreeUri(context, selection.treeUri) ?: return emptyList()
                root.listFiles()
                    .filter { it.isFile && it.name != null }
                    .map {
                        ArchivePartSource.UriPart(
                            name = it.name.orEmpty(),
                            uri = it.uri
                        )
                    }
            }

            is ArchiveSelection.LocalFile -> {
                val parent = File(selection.absolutePath).parentFile ?: return emptyList()
                parent.listFiles().orEmpty()
                    .filter { it.isFile }
                    .map {
                        ArchivePartSource.LocalPart(
                            name = it.name,
                            absolutePath = it.absolutePath
                        )
                    }
            }

            is ArchiveSelection.MultipleUris -> selection.parts
            is ArchiveSelection.SingleUri -> emptyList()
        }
    }
}
