define(
	[
		openhmis.url.backboneBase + 'js/view/generic',
        openhmis.url.backboneBase + 'js/view/openhmis',
		openhmis.url.inventoryBase + 'js/model/operation',
		openhmis.url.inventoryBase + 'js/model/stockroom',
		'link!' + openhmis.url.inventoryBase + 'css/style.css'
	],
	function(openhmis) {
		openhmis.OperationTypeEditView = openhmis.CustomizableInstanceTypeAddEditView.extend({
			tmplFile: openhmis.url.inventoryBase + 'template/operation.html',
			tmplSelector: '#detail-template'
		});

        openhmis.MyOperationListView = openhmis.GenericSearchableListView.extend({
            fetch: function(options, sender) {
                options = options ? options : {};

                // The 'my' query is set to return the list of user operations on the operation resource
                options.queryString = openhmis.addQueryStringParameter(options.queryString, "q=my");

                openhmis.GenericListView.prototype.fetch.call(this, options, sender);
            }
        });

        openhmis.OperationItemListItemView = openhmis.GenericListItemView.extend({
            tmplFile: openhmis.url.inventoryBase + 'template/operation.html',
            tmplSelector: '#operation-item-list-item'
        });

        openhmis.MyOperationDetailView = openhmis.GenericAddEditView.extend({
            tmplFile: openhmis.url.inventoryBase + 'template/operation.html',
            tmplSelector: '#view-operation-detail',

            events: {
                'click .completeOp': 'completeOperation',
                'click .cancelOp': 'cancelOperation',
                'click .cancel': 'cancel'
            },

            initialize: function(options) {
                openhmis.GenericAddEditView.prototype.initialize.call(this, options);

                this.itemsView = new openhmis.GenericListView({
                    model: new openhmis.GenericCollection([], {
                        model: openhmis.OperationItem
                    }),
                    showRetiredOption: false,
                    showRetired: true,
                    listTitle: "Operation Items",
                    listFields: ['item', 'quantity', 'batchOperation', 'expiration'],
                    itemView: openhmis.OperationItemListItemView
                });

                this.itemsView.on("fetch", this.fetch);
            },

            completeOperation: function() {
                var self = this;
                $.ajax({
                    type: 'POST',
                    url: this.model.url(),
                    data: '{"status":"COMPLETED"}',
                    success: function(data) {
                        self.model.fetch();

                        self.cancel();
                    },
                    contentType: "application/json",
                    dataType: 'json'
                });

                //this.model.set('status', 'COMPLETED');
                //this.save();
            },

            cancelOperation: function() {
                this.model.set('status', 'CANCELLED');

                this.save();
            },

            prepareModelForm: function(model, options) {
              // We don't want to use the backbone form as we're just displaying the operation information here
                return undefined;
            },

            fetch: function(options) {
                options.queryString = openhmis.addQueryStringParameter(options.queryString, "operation_uuid=" + this.model.id);
            },

            render: function() {
                openhmis.GenericAddEditView.prototype.render.call(this);

                if (this.model.id) {
                    // Fetch and render the operation items list
                    this.itemsView.fetch(undefined, undefined);

                    var itemsEl = $("#operation-items");
                    itemsEl.append(this.itemsView.el);
                    itemsEl.show();
                }
            }
        });

		return openhmis;
	}
);
