/*
 * controllers for html pieces
 */
define(function () {
    var mainController = function($scope, $location, Upload, $timeout, $log, $window){
        $scope.path = '';
        $scope.uploadFiles = function (files) {
            $scope.files = files;            
            var data = {};
            data.savePath = $scope.path;
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
    mainController.$inject = ['$scope', '$location', 'Upload', '$timeout', '$log', '$window'];

    return {mainController: mainController};
});
