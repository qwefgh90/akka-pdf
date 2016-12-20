/*
 * controllers for html pieces
 */
define(function () {
    var mainController = function($scope, $location, Upload, $timeout, $log, $window, $http, $httpParamSerializer){
        $scope.path = '';
        $scope.url = '';
        
        $scope.sample = [{'label':'1'},{'label':'2'},{'label':'3'},{'label':'4'},{'label':'5'}];
        $scope.selected = false;
        
        $scope.model = {
            binaries : [],
            files : []
        };

        $scope.$watch('dropFiles', function (files) {
            if(typeof files == 'object'){
                files.forEach(function(f){
                    $scope.model.binaries.push(f);
                    $scope.model.files.push({'lastModified' : f.lastModified,
                                             'name' : f.name,
                                             'size' : f.size
                                            });
                    $log.debug(f.name);
                });
            }
        });

        $scope.uploadDropFiles = function(){
            var files = [];
            for(var i=0 ; i < $scope.model.files.length; i++){
                var fileInfo = $scope.model.files[i];
                for(var j=0; j < $scope.model.binaries.length; j++){
                    if(fileInfo.lastModified == $scope.model.binaries[j].lastModified
                       && fileInfo.name == $scope.model.binaries[j].name
                       && fileInfo.size == $scope.model.binaries[j].size){
                        files.push($scope.model.binaries[j]);
                        break;
                    }
                }
            }
            $scope.uploadFiles(files);
        };

        $scope.remove = function(index){
            $scope.model.files.splice(index, 1)
            $log.debug('start');
            $scope.model.files.forEach(function(f){
                $log.debug(f.name);
            });
            $log.debug('end');
        };

        $scope.webToPdf = function(){
            var data = {'url': $scope.url};
            var config = {
                headers : {
                    'content-type': 'application/x-www-form-urlencoded'
                }
            }

            var promise = $http.post('WebToPdf', $httpParamSerializer(data), config);
            promise.then(function (response) {//$log.info(angular.toJson(response));
                                             $log.info(response.data);
                    
                                             // var blob = new Blob([response.data], {type:"application/pdf"});
                                             // var objectUrl = URL.createObjectURL(blob);
                    $window.open(response.data.link, "_blank")
                                             $timeout(function () {
                                                 $scope.result = response.data;
                                             });
                                            }, function (response) {
                                                if (response.status > 0) {
                                                    $scope.errorMsg = response.status + ': ' + response.data;
                                                }
                                            }, function (evt) {
                                                $scope.progress = 
                                                    Math.min(100, parseInt(100.0 * evt.loaded / evt.total));
                                            });
        };
        $scope.uploadFiles = function (files) {
            $scope.files = files;            
            var data = {};
            //data.savePath = $scope.path;
            var i = 0;
            $scope.files.forEach(function(file){
                var key = 'file'+i;
                data[key] = file;
                i++;
            });
            if (files && files.length) {
                Upload.upload({
                    url: '/MemoryMergeToMemory',
                    data: data
                }).then(function (response) {//$log.info(angular.toJson(response));
                                             $log.info(response.data);
                    
                                             // var blob = new Blob([response.data], {type:"application/pdf"});
                                             // var objectUrl = URL.createObjectURL(blob);
                    $window.open(response.data.link, "_blank")
                                             $timeout(function () {
                                                 $scope.result = response.data;
                                             });
                                            }, function (response) {
                                                if (response.status > 0) {
                                                    $scope.errorMsg = response.status + ': ' + response.data;
                                                }
                                            }, function (evt) {
                                                $scope.progress = 
                                                    Math.min(100, parseInt(100.0 * evt.loaded / evt.total));
                                            });
            }
        };
    }
    mainController.$inject = ['$scope', '$location', 'Upload', '$timeout', '$log', '$window', '$http', '$httpParamSerializer'];

    return {mainController: mainController};
});
