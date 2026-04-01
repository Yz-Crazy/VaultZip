#pragma once

#include <optional>
#include <string>
#include <vector>

#include "ArchiveTypes.h"

namespace archivekit {

ArchiveFormatKind DetectFormat(const ArchiveSource& source);

std::vector<ArchiveEntryData> ListEntries(
    const ArchiveSource& source,
    const std::optional<std::u16string>& password
);

ExtractResultData Extract(
    const ArchiveSource& source,
    const std::string& outputDir,
    const std::optional<std::u16string>& password
);

ExtractSingleEntryResultData ExtractEntry(
    const ArchiveSource& source,
    const std::string& entryPath,
    const std::string& outputDir,
    const std::optional<std::u16string>& password
);

}  // namespace archivekit
