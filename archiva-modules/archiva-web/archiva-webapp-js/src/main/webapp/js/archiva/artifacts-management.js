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
define("archiva.artifacts-management",["jquery","i18n","utils","jquery.tmpl","knockout",
  "knockout.simpleGrid","jquery.validate","bootstrap","jquery.fileupload","jquery.fileupload.ui"]
    , function() {

  ArtifactUpload=function(classifier,pomFile){
    this.classifier=classifier;
    this.pomFile=pomFile;
  }

  ArtifactUploadViewModel=function(managedRepositories){
    var self=this;
    this.managedRepositories=ko.observableArray(managedRepositories);
    this.repositoryId=ko.observable();
    this.groupId=ko.observable();
    this.artifactId=ko.observable();
    this.version=ko.observable();
    this.packaging=ko.observable();
    this.generatePom=ko.observable();

    this.artifactUploads=[];

    saveArtifacts=function(){
      clearUserMessages();
      if(!$("#main-content" ).find("#fileupload" ).valid()){
        return;
      }
      if(this.artifactUploads.length<1){
        displayErrorMessage( $.i18n.prop("fileupload.upload.required"));
        return;
      }
      var url="restServices/archivaUiServices/fileUploadService/save/"+this.repositoryId()+"/"+this.groupId()+"/"+this.artifactId();
      url+="/"+this.version()+"/"+this.packaging();
      if (this.generatePom()){
        url+="?generatePom=true";
      }
      $.ajax(url, {
          type: "GET",
          dataType: 'json',
          success: function(data) {
            displaySuccessMessage($.i18n.prop("fileupload.artifacts.saved"));
          },
          error: function(data) {
            var res = $.parseJSON(data.responseText);
            displayRestError(res);
          }
        }
      );

    }

  }

  displayUploadArtifact=function(){
    screenChange();
    var mainContent=$("#main-content");
    mainContent.html(mediumSpinnerImg());


    $.ajax("restServices/archivaUiServices/fileUploadService/clearUploadedFiles", {
        type: "GET",
        dataType: 'json',
        success: function(data) {
          mainContent.html($("#file-upload-screen" ).html());
          $.ajax("restServices/archivaServices/browseService/userRepositories", {
              type: "GET",
              dataType: 'json',
              success: function(data) {
                var artifactUploadViewModel=new ArtifactUploadViewModel(data);
                ko.applyBindings(artifactUploadViewModel,mainContent.find("#file-upload-main" ).get(0));
                var fileUpload=$("#main-content" ).find("#fileupload");
                var validator =  fileUpload.validate({
                  showErrors: function(validator, errorMap, errorList) {
                   customShowError("#main-content #fileupload",validator,errorMap,errorMap);
                  }
                });

                fileUpload.fileupload({
                    submit: function (e, data) {
                      var $this = $(this);
                      $this.fileupload('send', data);
                      artifactUploadViewModel.artifactUploads.push(new ArtifactUpload(data.formData.classifier,data.formData.pomFile));
                      return false;
                    }
                  }
                );
                fileUpload.bind('fileuploadsubmit', function (e, data) {
                  var pomFile = data.context.find('#pomFile' ).attr("checked");
                  var classifier = data.context.find('#classifier' ).val();
                  if (!data.formData){
                    data.formData={};
                  }
                  data.formData.pomFile = pomFile;
                  data.formData.classifier = classifier;
                });
              }
          });
      }
    });
  }

});