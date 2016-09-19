/*
 * controllers for html pieces
 */
define(function () {
    var mainController = function($scope, $location, Upload, $timeout){
        $scope.uploadFiles = function (files) {
            $scope.files = files;
            if (files && files.length) {
                Upload.upload({
                    url: '/MemoryMergeToMemory',
                    data: {
                        files: files,
                        meta: {'name': {'lastname': 'choe', 'firstname': 'changwon'}, 'age': [20,19,18]},
                        meta2: 'this is meta2'
                    }
                }).then(function (response) {
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
    mainController.$inject = ['$scope', '$location', 'Upload', '$timeout'];

    return {mainController: mainController};
});
