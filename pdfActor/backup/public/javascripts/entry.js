/*
 * requirejs entry point
 * declare path, shim (for module which not support AMD), require()
 */
require.config({
	paths: {
        jquery: '../lib/jquery/jquery',
		angular : '../lib/angularjs/angular',
        ngFileUpload : '../lib/ng-file-upload/ng-file-upload',
        angularBootstrap : '../lib/angular-ui-bootstrap/dist/ui-bootstrap-tpls'
	},
	shim: {
        angular: {exports: 'angular'},
        angularBootstrap: {deps: ['angular'], exports: 'angular'},
        ngFileUpload: {deps: ['angular'], exports: 'angular'}
	}
});

require(['angular', './controllers', 'jquery', 'ngFileUpload', 'angularBootstrap'], function(angular, controllers){
    
    var app = angular.module('mainApp',['ngFileUpload','ui.bootstrap']);
    app.controller('MainController', controllers.mainController);
	angular.bootstrap(document, ['mainApp']);
});
