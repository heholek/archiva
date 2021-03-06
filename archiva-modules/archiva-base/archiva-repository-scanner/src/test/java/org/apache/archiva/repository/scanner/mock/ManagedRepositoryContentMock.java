package org.apache.archiva.repository.scanner.mock;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.archiva.common.filelock.DefaultFileLockManager;
import org.apache.archiva.common.utils.VersionUtil;
import org.apache.archiva.metadata.model.ArtifactMetadata;
import org.apache.archiva.metadata.model.maven2.MavenArtifactFacet;
import org.apache.archiva.model.ArchivaArtifact;
import org.apache.archiva.model.ArtifactReference;
import org.apache.archiva.model.ProjectReference;
import org.apache.archiva.model.VersionedReference;
import org.apache.archiva.repository.*;
import org.apache.archiva.repository.storage.FilesystemStorage;
import org.apache.archiva.repository.storage.StorageAsset;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Martin Stockhammer <martin_s@apache.org>
 */
public class ManagedRepositoryContentMock implements ManagedRepositoryContent
{
    private static final String PATH_SEPARATOR = "/";
    private static final String GROUP_SEPARATOR = ".";
    public static final String MAVEN_METADATA = "maven-metadata.xml";


    private ManagedRepository repository;
    private FilesystemStorage fsStorage;

    public ManagedRepositoryContentMock(ManagedRepository repo) {
        this.repository = repo;
    }

    @Override
    public void deleteVersion( VersionedReference reference ) throws ContentNotFoundException
    {

    }

    @Override
    public void deleteArtifact( ArtifactReference artifactReference ) throws ContentNotFoundException
    {

    }

    @Override
    public void deleteGroupId( String groupId ) throws ContentNotFoundException
    {

    }

    @Override
    public void deleteProject( String namespace, String projectId ) throws RepositoryException
    {

    }

    @Override
    public String getId( )
    {
        return repository.getId();
    }

    @Override
    public Set<ArtifactReference> getRelatedArtifacts( ArtifactReference reference ) throws ContentNotFoundException
    {
        return null;
    }

    @Override
    public String getRepoRoot( )
    {
        return getRepoRootAsset().getFilePath().toString();
    }

    private StorageAsset getRepoRootAsset() {
        if (fsStorage==null) {
            try {
                fsStorage = new FilesystemStorage(Paths.get("", "target", "test-repository", "managed"), new DefaultFileLockManager());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return fsStorage.getAsset("");
    }

    @Override
    public ManagedRepository getRepository( )
    {
        return repository;
    }

    @Override
    public Set<String> getVersions( ProjectReference reference ) throws ContentNotFoundException, LayoutException
    {
        return null;
    }

    @Override
    public Set<String> getVersions( VersionedReference reference ) throws ContentNotFoundException
    {
        return null;
    }

    @Override
    public boolean hasContent( ArtifactReference reference )
    {
        return false;
    }

    @Override
    public boolean hasContent( ProjectReference reference )
    {
        return false;
    }

    @Override
    public boolean hasContent( VersionedReference reference )
    {
        return false;
    }

    @Override
    public void setRepository( ManagedRepository repo )
    {
        this.repository = repo;
    }

    private Map<ArtifactReference, String> refs = new HashMap<>();

    @Override
    public ArtifactReference toArtifactReference( String path ) throws LayoutException
    {
        if ( StringUtils.isBlank( path ) )
        {
            throw new LayoutException( "Unable to convert blank path." );
        }

        ArtifactMetadata metadata = getArtifactForPath("test-repository", path);

        ArtifactReference artifact = new ArtifactReference();
        artifact.setGroupId( metadata.getNamespace() );
        artifact.setArtifactId( metadata.getProject() );
        artifact.setVersion( metadata.getVersion() );
        MavenArtifactFacet facet = (MavenArtifactFacet) metadata.getFacet( MavenArtifactFacet.FACET_ID );
        if ( facet != null )
        {
            artifact.setClassifier( facet.getClassifier() );
            artifact.setType( facet.getType() );
        }
        refs.put(artifact, path);
        return artifact;
    }

    public ArtifactMetadata getArtifactForPath( String repoId, String relativePath )
    {
        String[] parts = relativePath.replace( '\\', '/' ).split( "/" );

        int len = parts.length;
        if ( len < 4 )
        {
            throw new IllegalArgumentException(
                    "Not a valid artifact path in a Maven 2 repository, not enough directories: " + relativePath );
        }

        String id = parts[--len];
        String baseVersion = parts[--len];
        String artifactId = parts[--len];
        StringBuilder groupIdBuilder = new StringBuilder();
        for ( int i = 0; i < len - 1; i++ )
        {
            groupIdBuilder.append( parts[i] );
            groupIdBuilder.append( '.' );
        }
        groupIdBuilder.append( parts[len - 1] );

        return getArtifactFromId( repoId, groupIdBuilder.toString(), artifactId, baseVersion, id );
    }

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile( "([0-9]{8}.[0-9]{6})-([0-9]+).*" );



    public ArtifactMetadata getArtifactFromId( String repoId, String namespace, String projectId, String projectVersion,
                                               String id )
    {
        if ( !id.startsWith( projectId + "-" ) )
        {
            throw new IllegalArgumentException( "Not a valid artifact path in a Maven 2 repository, filename '" + id
                    + "' doesn't start with artifact ID '" + projectId + "'" );
        }

        MavenArtifactFacet facet = new MavenArtifactFacet();

        int index = projectId.length() + 1;
        String version;
        String idSubStrFromVersion = id.substring( index );
        if ( idSubStrFromVersion.startsWith( projectVersion ) && !VersionUtil.isUniqueSnapshot( projectVersion ) )
        {
            // non-snapshot versions, or non-timestamped snapshot versions
            version = projectVersion;
        }
        else if ( VersionUtil.isGenericSnapshot( projectVersion ) )
        {
            // timestamped snapshots
            try
            {
                int mainVersionLength = projectVersion.length() - 8; // 8 is length of "SNAPSHOT"
                if ( mainVersionLength == 0 )
                {
                    throw new IllegalArgumentException(
                            "Timestamped snapshots must contain the main version, filename was '" + id + "'" );
                }

                Matcher m = TIMESTAMP_PATTERN.matcher( idSubStrFromVersion.substring( mainVersionLength ) );
                m.matches();
                String timestamp = m.group( 1 );
                String buildNumber = m.group( 2 );
                facet.setTimestamp( timestamp );
                facet.setBuildNumber( Integer.parseInt( buildNumber ) );
                version = idSubStrFromVersion.substring( 0, mainVersionLength ) + timestamp + "-" + buildNumber;
            }
            catch ( IllegalStateException e )
            {
                throw new IllegalArgumentException( "Not a valid artifact path in a Maven 2 repository, filename '" + id
                        + "' doesn't contain a timestamped version matching snapshot '"
                        + projectVersion + "'", e);
            }
        }
        else
        {
            // invalid
            throw new IllegalArgumentException(
                    "Not a valid artifact path in a Maven 2 repository, filename '" + id + "' doesn't contain version '"
                            + projectVersion + "'" );
        }

        String classifier;
        String ext;
        index += version.length();
        if ( index == id.length() )
        {
            // no classifier or extension
            classifier = null;
            ext = null;
        }
        else
        {
            char c = id.charAt( index );
            if ( c == '-' )
            {
                // classifier up until '.'
                int extIndex = id.indexOf( '.', index );
                if ( extIndex >= 0 )
                {
                    classifier = id.substring( index + 1, extIndex );
                    ext = id.substring( extIndex + 1 );
                }
                else
                {
                    classifier = id.substring( index + 1 );
                    ext = null;
                }
            }
            else if ( c == '.' )
            {
                // rest is the extension
                classifier = null;
                ext = id.substring( index + 1 );
            }
            else
            {
                throw new IllegalArgumentException( "Not a valid artifact path in a Maven 2 repository, filename '" + id
                        + "' expected classifier or extension but got '"
                        + id.substring( index ) + "'" );
            }
        }

        ArtifactMetadata metadata = new ArtifactMetadata();
        metadata.setId( id );
        metadata.setNamespace( namespace );
        metadata.setProject( projectId );
        metadata.setRepositoryId( repoId );
        metadata.setProjectVersion( projectVersion );
        metadata.setVersion( version );

        facet.setClassifier( classifier );

        // we use our own provider here instead of directly accessing Maven's artifact handlers as it has no way
        // to select the correct order to apply multiple extensions mappings to a preferred type
        // TODO: this won't allow the user to decide order to apply them if there are conflicts or desired changes -
        //       perhaps the plugins could register missing entries in configuration, then we just use configuration
        //       here?

        String type = null;


        // use extension as default
        if ( type == null )
        {
            type = ext;
        }

        // TODO: should we allow this instead?
        if ( type == null )
        {
            throw new IllegalArgumentException(
                    "Not a valid artifact path in a Maven 2 repository, filename '" + id + "' does not have a type" );
        }

        facet.setType( type );
        metadata.addFacet( facet );

        return metadata;
    }


    @Override
    public StorageAsset toFile( ArtifactReference reference )
    {
        return getRepoRootAsset().resolve(refs.get(reference));
    }

    @Override
    public StorageAsset toFile( ArchivaArtifact reference )
    {
        return null;
    }

    private String formatAsDirectory( String directory )
    {
        return directory.replace( GROUP_SEPARATOR, PATH_SEPARATOR );
    }

    public String toMetadataPath( ProjectReference reference )
    {
        StringBuilder path = new StringBuilder();

        path.append( formatAsDirectory( reference.getGroupId() ) ).append( PATH_SEPARATOR );
        path.append( reference.getArtifactId() ).append( PATH_SEPARATOR );
        path.append( MAVEN_METADATA );

        return path.toString();
    }

    public String toMetadataPath( VersionedReference reference )
    {
        StringBuilder path = new StringBuilder();

        path.append( formatAsDirectory( reference.getGroupId() ) ).append( PATH_SEPARATOR );
        path.append( reference.getArtifactId() ).append( PATH_SEPARATOR );
        if ( reference.getVersion() != null )
        {
            // add the version only if it is present
            path.append( VersionUtil.getBaseVersion( reference.getVersion() ) ).append( PATH_SEPARATOR );
        }
        path.append( MAVEN_METADATA );

        return path.toString();
    }

    @Override
    public String toPath( ArtifactReference reference )
    {
        return null;
    }

    @Override
    public String toPath( ArchivaArtifact reference )
    {
        return null;
    }

}
