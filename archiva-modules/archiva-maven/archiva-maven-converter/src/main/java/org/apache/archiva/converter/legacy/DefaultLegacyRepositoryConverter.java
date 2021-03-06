package org.apache.archiva.converter.legacy;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.archiva.common.filelock.DefaultFileLockManager;
import org.apache.archiva.common.plexusbridge.PlexusSisuBridge;
import org.apache.archiva.common.plexusbridge.PlexusSisuBridgeException;
import org.apache.archiva.common.utils.PathUtil;
import org.apache.archiva.configuration.FileTypes;
import org.apache.archiva.consumers.InvalidRepositoryContentConsumer;
import org.apache.archiva.consumers.KnownRepositoryContentConsumer;
import org.apache.archiva.converter.RepositoryConversionException;
import org.apache.archiva.repository.base.BasicManagedRepository;
import org.apache.archiva.repository.storage.FilesystemStorage;
import org.apache.archiva.repository.content.maven2.ManagedDefaultRepositoryContent;
import org.apache.archiva.repository.scanner.RepositoryScanner;
import org.apache.archiva.repository.scanner.RepositoryScannerException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * DefaultLegacyRepositoryConverter
 *
 *
 */
@Service( "legacyRepositoryConverter#default" )
public class DefaultLegacyRepositoryConverter
    implements LegacyRepositoryConverter
{
    /**
     *
     */
    // private ArtifactRepositoryFactory artifactRepositoryFactory;

    /**
     *
     */
    private ArtifactRepositoryLayout defaultLayout;

    @Inject
    FileTypes fileTypes;

    /**
     *
     */
    @Inject
    @Named( value = "knownRepositoryContentConsumer#artifact-legacy-to-default-converter" )
    private LegacyConverterArtifactConsumer legacyConverterConsumer;

    /**
     *
     */
    @Inject
    private RepositoryScanner repoScanner;

    @Inject
    public DefaultLegacyRepositoryConverter( PlexusSisuBridge plexusSisuBridge )
        throws PlexusSisuBridgeException
    {
        // artifactRepositoryFactory = plexusSisuBridge.lookup( ArtifactRepositoryFactory.class );
        defaultLayout = plexusSisuBridge.lookup( ArtifactRepositoryLayout.class, "default" );
    }

    @Override
    public void convertLegacyRepository( Path legacyRepositoryDirectory, Path repositoryDirectory,
                                         List<String> fileExclusionPatterns )
        throws RepositoryConversionException
    {
        try
        {
            String defaultRepositoryUrl = PathUtil.toUrl( repositoryDirectory );

            BasicManagedRepository legacyRepository = BasicManagedRepository.newFilesystemInstance( "legacy", "Legacy Repository", repositoryDirectory);
            legacyRepository.setLocation( legacyRepositoryDirectory.toAbsolutePath().toUri() );
            legacyRepository.setLayout( "legacy" );
            DefaultFileLockManager lockManager = new DefaultFileLockManager();
            FilesystemStorage storage = new FilesystemStorage(legacyRepositoryDirectory, lockManager);
            legacyRepository.setContent(new ManagedDefaultRepositoryContent(legacyRepository, fileTypes, lockManager));

            ArtifactRepository repository =
                new MavenArtifactRepository("default", defaultRepositoryUrl, defaultLayout, null, null);

            legacyConverterConsumer.setExcludes( fileExclusionPatterns );
            legacyConverterConsumer.setDestinationRepository( repository );

            List<KnownRepositoryContentConsumer> knownConsumers = new ArrayList<>( 1 );
            knownConsumers.add( legacyConverterConsumer );

            List<InvalidRepositoryContentConsumer> invalidConsumers = Collections.emptyList();
            List<String> ignoredContent = new ArrayList<String>( Arrays.asList( RepositoryScanner.IGNORABLE_CONTENT ) );

            repoScanner.scan( legacyRepository, knownConsumers, invalidConsumers, ignoredContent,
                              RepositoryScanner.FRESH_SCAN );
        }
        catch (RepositoryScannerException | IOException e )
        {
            throw new RepositoryConversionException( "Error convering legacy repository.", e );
        }
    }
}
