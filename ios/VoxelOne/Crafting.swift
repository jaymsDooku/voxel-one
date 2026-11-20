import UIKit

/// One recipe transaction per tap. The controller owns inventory, persistence and authority.
@MainActor final class NativeCraftingScreen:UIViewController,UITableViewDataSource,UITableViewDelegate,UISearchBarDelegate {
    private let recipes:[NativeRecipe]
    private let name:(Int)->String
    private let search=UISearchBar()
    private let table=UITableView(frame:.zero,style:.plain)
    private let status=UILabel()
    private let emptyState=UILabel()
    private let emptySpace=UIView()
    private let category=UISegmentedControl(items:["All","Tools","Mat","Build","Decor","Light"])
    private var shown:[NativeRecipe]=[]
    private var inventory:[[Int]]=[]
    private var pending=false
    private var queryText=""
    private let quantity=UIStepper()
    private let quantityLabel=UILabel()
    private let maximumBatch:Int
    var onCraft:((NativeRecipe,Int)->Void)?
    var onClose:(()->Void)?
    init(recipes:[NativeRecipe],maximumBatch:Int,name:@escaping (Int)->String){self.recipes=recipes;self.maximumBatch=min(64,max(1,maximumBatch));self.name=name;super.init(nibName:nil,bundle:nil)}
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
    override func viewDidLoad(){
        super.viewDidLoad();view.backgroundColor = .systemBackground
        let title=UILabel();title.text="Craft";title.font = .preferredFont(forTextStyle:.title2)
        let done=UIButton(type:.system);done.setTitle("Done",for:.normal);done.accessibilityIdentifier="craftDone";done.addTarget(self,action:#selector(close),for:.touchUpInside)
        quantity.minimumValue=1;quantity.maximumValue=Double(maximumBatch);quantity.value=1;quantity.stepValue=1
        quantity.accessibilityIdentifier="craftQuantity";quantity.addTarget(self,action:#selector(quantityChanged),for:.valueChanged)
        quantityLabel.text="Batch: 1";quantityLabel.accessibilityIdentifier="craftQuantityLabel"
        let batch=UIStackView(arrangedSubviews:[quantityLabel,quantity]);batch.distribution = .equalSpacing
        let header=UIStackView(arrangedSubviews:[title,done]);header.distribution = .equalSpacing
        search.placeholder="Find recipe or ingredient";search.accessibilityIdentifier="craftSearchBar";search.searchTextField.accessibilityIdentifier="craftSearch";search.delegate=self
        search.searchTextField.addTarget(self,action:#selector(queryChanged),for:.editingChanged)
        category.selectedSegmentIndex=0;category.addTarget(self,action:#selector(filter),for:.valueChanged);category.accessibilityIdentifier="craftCategory"
        status.numberOfLines=0;status.font = .preferredFont(forTextStyle:.footnote);status.accessibilityIdentifier="craftStatus"
        emptyState.text="No matching recipes.";emptyState.textColor = .secondaryLabel
        emptyState.accessibilityIdentifier="craftEmptyState";emptyState.numberOfLines=0;emptyState.isHidden=true;emptySpace.isHidden=true
        table.dataSource=self;table.delegate=self;table.accessibilityIdentifier="recipeTable";table.keyboardDismissMode = .onDrag
        table.rowHeight=UITableView.automaticDimension;table.estimatedRowHeight=76
        let stack=UIStackView(arrangedSubviews:[header,search,category,batch,status,emptyState,emptySpace,table]);stack.axis = .vertical;stack.spacing=8;stack.translatesAutoresizingMaskIntoConstraints=false
        view.addSubview(stack);let safe=view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo:safe.leadingAnchor,constant:12),stack.trailingAnchor.constraint(equalTo:safe.trailingAnchor,constant:-12),stack.topAnchor.constraint(equalTo:safe.topAnchor,constant:8),stack.bottomAnchor.constraint(equalTo:safe.bottomAnchor,constant:-8),done.heightAnchor.constraint(greaterThanOrEqualToConstant:44),category.heightAnchor.constraint(greaterThanOrEqualToConstant:36)])
        NotificationCenter.default.addObserver(self,selector:#selector(keyboardChanged),name:UIResponder.keyboardDidChangeFrameNotification,object:nil)
        filter()
    }
    @objc private func keyboardChanged(){view.setNeedsLayout()}
    override func viewDidLayoutSubviews(){
        super.viewDidLayoutSubviews()
        // Keep Done/search/category in the safe area even with a landscape keyboard.
        // Only the scrolling recipe list needs clearance from the keyboard.
        let bottom=table.convert(table.bounds,to:view).maxY
        let overlap=max(0,bottom-view.keyboardLayoutGuide.layoutFrame.minY)
        if table.contentInset.bottom != overlap {
            table.contentInset.bottom=overlap;table.verticalScrollIndicatorInsets.bottom=overlap
        }
    }
    func update(inventory:[[Int]],message:String,pending:Bool){
        loadViewIfNeeded();if status.text != message {status.text=message}
        guard self.inventory != inventory || self.pending != pending else{return}
        table.isUserInteractionEnabled = !pending;quantity.isEnabled = !pending && maximumBatch>1
        self.inventory=inventory;self.pending=pending;table.reloadData()
    }
    @objc private func close(){view.endEditing(true);let finish=onClose;dismiss(animated:true){finish?()}}
    @objc private func quantityChanged(){view.endEditing(true);quantityLabel.text="Batch: \(Int(quantity.value))";table.reloadData()}
    @objc private func queryChanged(){queryText=search.searchTextField.text ?? "";filter()}
    @objc private func filter(){
        let query=queryText.trimmingCharacters(in:.whitespacesAndNewlines)
        let chosen=["All","Tools","Materials","Building","Decorations","Lighting"][category.selectedSegmentIndex]
        shown=recipes.filter { recipe in
            let text=([recipe.name,name(recipe.output)]+recipe.inputs.map{name($0[0])}).joined(separator:" ")
            return (chosen=="All" || recipe.category==chosen) && (query.isEmpty || text.localizedCaseInsensitiveContains(query))
        }
        emptyState.isHidden = !shown.isEmpty;emptyState.accessibilityValue="\(shown.count) recipes"
        emptySpace.isHidden = !shown.isEmpty
        table.isHidden=shown.isEmpty;table.accessibilityValue="\(shown.count) recipes";table.reloadData()
    }
    func searchBar(_ searchBar:UISearchBar,textDidChange searchText:String){queryText=searchText;filter()}
    func searchBarSearchButtonClicked(_ searchBar:UISearchBar){view.endEditing(true)}
    func tableView(_ tableView:UITableView,numberOfRowsInSection section:Int)->Int {shown.count}
    func tableView(_ tableView:UITableView,cellForRowAt indexPath:IndexPath)->UITableViewCell {
        let cell=tableView.dequeueReusableCell(withIdentifier:"recipe") ?? UITableViewCell(style:.subtitle,reuseIdentifier:"recipe")
        let recipe=shown[indexPath.row]
        cell.textLabel?.text="\(recipe.name) → \(recipe.count*Int(quantity.value)) \(name(recipe.output))";cell.textLabel?.numberOfLines=0
        cell.detailTextLabel?.text=recipe.inputs.map { item in
            let owned=inventory.filter{$0[0]==item[0]}.reduce(0){$0+$1[1]}
            return "\(name(item[0])) \(owned)/\(item[1]*Int(quantity.value))"
        }.joined(separator:" · ");cell.detailTextLabel?.numberOfLines=0
        cell.accessibilityIdentifier="recipe\(recipe.id)";cell.accessoryType = .disclosureIndicator
        cell.textLabel?.textColor = pending ? .secondaryLabel:.label
        return cell
    }
    func tableView(_ tableView:UITableView,didSelectRowAt indexPath:IndexPath){
        tableView.deselectRow(at:indexPath,animated:true);guard !pending else{return};view.endEditing(true);onCraft?(shown[indexPath.row],Int(quantity.value))
    }
}
