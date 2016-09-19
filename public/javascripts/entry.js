/*
 * requirejs entry point
 * declare path, shim (for module which not support AMD), require()
 */
require.config({
	paths: {
        jquery: '../lib/jquery/jquery',
		angular : '../lib/angularjs/angular',
        ngFileUpload : '../lib/ng-file-upload/ng-file-upload'
        
	},
	shim: {
        angular: {exports: 'angular'},
        ngFileUpload: {deps: ['angular'], exports: 'angular'}
	}
});

require(['angular', './controllers', 'jquery', 'ngFileUpload'], function(angular, controllers){
    
    var app = angular.module('mainApp',['ngFileUpload']);
    app.controller('MainController', controllers.mainController);
	angular.bootstrap(document, ['mainApp']);
});
